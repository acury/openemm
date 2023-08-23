/********************************************************************************************************************************************************************************************************************************************************************
 *                                                                                                                                                                                                                                                                  *
 *                                                                                                                                                                                                                                                                  *
 *        Copyright (C) 2022 AGNITAS AG (https://www.agnitas.org)                                                                                                                                                                                                   *
 *                                                                                                                                                                                                                                                                  *
 *        This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.    *
 *        This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.           *
 *        You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.                                                                                                            *
 *                                                                                                                                                                                                                                                                  *
 ********************************************************************************************************************************************************************************************************************************************************************/
# include	<ctype.h>
# include	"xmlback.h"

# define	ST_INITIAL		0
# define	ST_PLAIN_START_FOUND	(-1)
# define	ST_QUOTED_START_FOUND	(-2)
# define	ST_END_FOUND		(-3)
# define	SWAP(bbb)		do { xmlBufferPtr __temp = (bbb) -> in; (bbb) -> in = (bbb) -> out; (bbb) -> out = __temp; } while (0)

static bool_t
transcode_url_for_content (blockmail_t *blockmail, xmlBufferPtr url, const char *uid) /*{{{*/
{
	bool_t		rc = false;
	const xmlChar	*ptr = xmlBufferContent (url);
	int		size = xmlBufferLength (url);
	int		state = 0;
	xmlChar		ch;
	
	buffer_clear (blockmail -> link_maker);
	while (size > 0) {
		ch = *ptr++;
		--size;
		if (state < 3) {
			if (ch == '/')
				++state;
		} else if (state == 3) {
			if (isalpha (ch)) {
				buffer_appendch (blockmail -> link_maker, ch);
				buffer_appendch (blockmail -> link_maker, '/');
				buffer_appends (blockmail -> link_maker, uid);
				buffer_appendch (blockmail -> link_maker, '/');
				rc = true;
			}
			++state;
		} else if ((ch == '?') || (ch == '&'))
			break;
		buffer_appendch (blockmail -> link_maker, ch);
	}
	return rc;
}/*}}}*/
static void
mkautourl (blockmail_t *blockmail, receiver_t *rec, block_t *block, url_t *url, record_t *record) /*{{{*/
{
	char	*uid;

	if (blockmail -> auto_url_is_dynamic && (uid = create_uid (blockmail, blockmail -> uid_version, blockmail -> auto_url_prefix, rec, url -> url_id))) {
		char	parameter_separator[2];

		parameter_separator[1] = '\0';
		if (blockmail -> rdir_content_links && transcode_url_for_content (blockmail, blockmail -> auto_url, uid)) {
			xmlBufferAdd (block -> out, buffer_content (blockmail -> link_maker), buffer_length (blockmail -> link_maker));
			parameter_separator[0] = '?';
		} else {
			xmlBufferAdd (block -> out, xmlBufferContent (blockmail -> auto_url), xmlBufferLength (blockmail -> auto_url));
			xmlBufferCCat (block -> out, "uid=");
			xmlBufferCCat (block -> out, uid);
			parameter_separator[0] = '&';
		}
		free (uid);
		if (record -> ids) {
			var_t		*temp;
			bool_t		first;
			const char	*ref;
			
			encrypt_build_reset (rec -> encrypt);
			for (temp = record -> ids, first = true; temp; temp = temp -> next) {
				if (first)
					first = false;
				else
					encrypt_build_add (rec -> encrypt, ",", 1);
				encrypt_build_add (rec -> encrypt, temp -> var, strlen (temp -> var));
				encrypt_build_add (rec -> encrypt, "=", 1);
				encrypt_build_add (rec -> encrypt, temp -> val, strlen (temp -> val));
			}
			if (ref = encrypt_build_do (rec -> encrypt, rec, 0)) {
				xmlBufferCCat (block -> out, parameter_separator);
				xmlBufferCCat (block -> out, "ref=");
				xmlBufferCCat (block -> out, ref);
				parameter_separator[0] = '&';
			}
		}
	} else
		xmlBufferAdd (block -> out, xmlBufferContent (blockmail -> auto_url), xmlBufferLength (blockmail -> auto_url));
}/*}}}*/
static const char *
mkonepixellogurl (blockmail_t *blockmail, receiver_t *rec) /*{{{*/
{
	char	*uid;
	
	if (blockmail -> onepixel_url && (uid = create_uid (blockmail, blockmail -> uid_version, NULL, rec, 0))) {
		if ((! blockmail -> rdir_content_links) || (! transcode_url_for_content (blockmail, blockmail -> onepixel_url, uid))) {
			buffer_set (blockmail -> link_maker, xmlBufferContent (blockmail -> onepixel_url), xmlBufferLength (blockmail -> onepixel_url));
			buffer_appends (blockmail -> link_maker, "uid=");
			buffer_appends (blockmail -> link_maker, uid);
			if (blockmail -> gui)
				buffer_appends (blockmail -> link_maker, "&nocount=1");
		}
		free (uid);
		return buffer_length (blockmail -> link_maker) ? buffer_string (blockmail -> link_maker) : NULL;
	}
	return NULL;
}/*}}}*/
	
static bool_t
url_is_personal (const xmlChar *url, int len) /*{{{*/
{
	bool_t	rc;
	int	n;
	int	state;
	int	pos, count;
	
	rc = false;
	for (n = 0, state = 0, pos = 0, count = 0; (! rc) && (n < len); ++n)
		switch (state) {
		case 0:
			if (url[n] == '?')
				state = 1;
			break;
		case 1:
			if (url[n] == '=') {
				state = 3;
				pos = 0;
				count = 0;
			}
			break;
		case 2:
			if (url[n] == '#')
				state = 5;
			else if (url[n] == '&')
				state = 1;
			break;
		case 3:
			if (url[n] == '.') {
				if (pos == 0)
					state = 2;
				else {
					pos = 0;
					if (++count == 4)
						state = 4;
				}
			} else if (isalnum (url[n]))
				++count;
			else if (url[n] == '#')
				state = 5;
			else
				state = 2;
			break;
		case 4:
			if (isalnum (url[n])) {
				state = -1;
				rc = true;
			} else
				state = 2;
			break;
		case 5:
			if (url[n] == '#') {
				state = 6;
				pos = 0;
			} else
				state = 2;
			break;
		case 6:
			if (url[n] == '#') {
				if (pos == 0)
					state = 2;
				else
					state = 7;
			} else
				++pos;
			break;
		case 7:
			if (url[n] == '#') {
				state = -1;
				rc = true;
			} else {
				state = 6;
				++pos;
			}
			break;
		}
	return rc;
}/*}}}*/

typedef struct { /*{{{*/
	blockmail_t	*blockmail;
	receiver_t	*receiver;
	/*}}}*/
}	rplc_t;
static bool_t
replace_anon_hashtags (void *rp, buffer_t *output, const xchar_t *token, int tlen) /*{{{*/
{
	rplc_t	*replacer = (rplc_t *) rp;
	int	pos;
	xchar_t	*param;
	
	for (pos = 0; pos < tlen; ++pos)
		if (token[pos] == ':')
			break;
	if ((pos < tlen) && (param = malloc (tlen - pos))) {
		if (pos + 1 < tlen)
			memcpy (param, token + pos + 1, tlen - pos - 1);
		param[tlen - pos - 1] = 0;
	} else {
		param = NULL;
	}
	if ((pos == 5) && (! memcmp (token, "PUBID", 5))) {
		char	*source = NULL;
		char	*opts = NULL;
		char	*pubid;
		
		if (param) {
			source = (char *) param;
			if (opts = strchr (source, ':')) {
				*opts++ = '\0';
			}
		}
		if (pubid = create_pubid (replacer -> blockmail, replacer -> receiver, source, opts)) {
			buffer_appends (output, pubid);
			free (pubid);
		}
	}
	if (param)
		free (param);
	return true;
}/*}}}*/
static bool_t
modify_urls_original (blockmail_t *blockmail, receiver_t *rec, block_t *block, protect_t *protect, bool_t ishtml, record_t *record) /*{{{*/
{
	int		n;
	int		len;
	const xmlChar	*cont;
	int		lstore;
	int		state;
	char		ch, quote;
	int		start, end;
	int		mask;
	int		clen;
	bool_t		changed;
	purl_t		*scratch;
	
	scratch = NULL;
	xmlBufferEmpty (block -> out);
	len = xmlBufferLength (block -> in);
	cont = xmlBufferContent (block -> in);
	lstore = 0;
	state = ST_INITIAL;
	quote = '\0';
	if (ishtml) {
		mask = 2;
	} else {
		mask = 1;
	}
	start = -1;
	end = -1;
	changed = false;
	for (n = 0; n <= len; ) {
		if (n < len) {
			clen = xmlCharLength (cont[n]);
			if (protect) {
				if (n >= protect -> start) {
					if (n < protect -> end)
						n += clen;
					else 
						protect = protect -> next;
					continue;
				}
			}
			if ((clen > 1) || isascii ((char) cont[n])) {
				ch = clen == 1 ? (char) cont[n] : '\0';
				switch (state) {
				case ST_INITIAL:
					if (ishtml) {
						if (ch == '<') {
							state = 100;
						}
					} else if (strchr ("hm", tolower (ch))) {
						if (tolower (ch) == 'h') {
							state = 1;
						} else {
							state = 31;
						}
						start = n;
					}
					break;
# define	CHK(ccc)	do { if ((ccc) == ch) ++state; else state = ST_INITIAL; } while (0)
# define	CCHK(ccc)	do { if ((ccc) == tolower (ch)) ++state; else state = ST_INITIAL; } while (0)
				/* plain: http:// and https:// */
				case 1:		CCHK ('t');	break;
				case 2:		CCHK ('t');	break;
				case 3:		CCHK ('p');	break;
				case 4:
					++state;
					if (tolower (ch) == 's')
						break;
					/* Fall through . . . */
				case 5:		CHK (':');	break;
				case 6:		CHK ('/');	break;
				case 7:
					if (ch == '/')
						state = ST_PLAIN_START_FOUND;
					else
						state = ST_INITIAL;
					break;
				/* plain: mailto: */
				case 31:	CCHK ('a');	break;
				case 32:	CCHK ('i');	break;
				case 33:	CCHK ('l');	break;
				case 34:	CCHK ('t');	break;
				case 35:	CCHK ('o');	break;
				case 36:	CHK (':');	break;
				case 37:
					state = ST_PLAIN_START_FOUND;
					break;
				/* HTML */
				case 100:
					if ((tolower (ch) == 'a') || (tolower (ch) == 'v'))
						++state;
					else
						state = ST_INITIAL;
					break;
# define	HCHK(ccc)	do { if ((ccc) == tolower (ch)) ++state; else if ('>' == ch) state = ST_INITIAL; else state = 101; } while (0)
				case 101:	HCHK ('h');	break;
				case 102:	HCHK ('r');	break;
				case 103:	HCHK ('e');	break;
				case 104:	HCHK ('f');	break;
# undef		HCHK						
				case 105:	CHK ('=');	break;
				case 106:
					if ((ch == '"') || (ch == '\'')) {
						quote = ch;
						state = ST_QUOTED_START_FOUND;
						start = n + 1;
					} else {
						state = ST_PLAIN_START_FOUND;
						start = n;
					}
					break;
				case ST_PLAIN_START_FOUND:
					if (isspace (ch) || (ch == '>')) {
						end = n;
						state = ST_END_FOUND;
					}
					break;
				case ST_QUOTED_START_FOUND:
					if (isspace (ch) || (ch == quote)) {
						end = n;
						state = ST_END_FOUND;
					}
					break;
				default:
					log_out (blockmail -> lg, LV_ERROR, "modify_urls: invalid state %d at position %d", state, n);
					state = ST_INITIAL;
					break;
				}
# undef		CHK
# undef		CCHK					
			} else {
				if (state == ST_PLAIN_START_FOUND) {
					end = n;
					state = ST_END_FOUND;
				} else
					state = ST_INITIAL;
			}
			n += clen;
		} else {
			if (state == ST_PLAIN_START_FOUND) {
				end = n;
				state = ST_END_FOUND;
			}
			++n;
		}
		if (state == ST_END_FOUND) {
			int	ulen, m;
			url_t	*match;
			int	first_orig = -1;

			ulen = end - start;
			for (m = 0, match = NULL; m < blockmail -> url_count; ++m) {
				if (url_match (blockmail -> url[m], cont + start, ulen)) {
					match = blockmail -> url[m];
					if (blockmail -> url[m] -> usage & mask)
						break;
				}
				if ((first_orig == -1) && blockmail -> url[m] -> orig) {
					first_orig = m;
				}
			}
			if (lstore < start) {
				xmlBufferAdd (block -> out, cont + lstore, start - lstore);
				lstore = start;
			}
			if ((match == NULL) && (first_orig != -1)) {
				for (m = first_orig; m < blockmail -> url_count; ++m) {
					if (url_match_original (blockmail -> url[m], cont + start, ulen)) {
						match = blockmail -> url[m];
						if (blockmail -> url[m] -> usage & mask)
							break;
					}
				}
			}
			if (blockmail -> anon) {
				if (! blockmail -> anon_preserve_links) {
					if ((m == blockmail -> url_count) || blockmail -> url[m] -> admin_link) {
						xmlBufferAdd (block -> out, (const xmlChar *) "#", 1);
						lstore = end;
						changed = true;
					} else if (url_is_personal (cont + start, ulen)) {
						if (! scratch)
							scratch = purl_alloc (NULL);
						if (scratch && purl_parsen (scratch, cont + start, ulen)) {
							const xchar_t	*rplc;
							int		rlen;
							rplc_t		replacer = { blockmail, rec };
							
							if ((rplc = purl_build (scratch, NULL, & rlen, replace_anon_hashtags, & replacer)) && rlen)
								xmlBufferAdd (block -> out, (const xmlChar *) rplc, rlen);
							lstore = end;
							changed = true;
						}
					}
				}
			} else if (m < blockmail -> url_count) {
				mkautourl (blockmail, rec, block, blockmail -> url[m], record);
				lstore = end;
				changed = true;
			} else if (match && match -> resolved) {
				const xmlChar	*url = NULL;
				int		ulength = -1;
				buffer_t	*resolve;
				
				if (resolve = link_resolve_get (match -> resolved, blockmail, block, match, rec, record)) {
					url = resolve -> buffer;
					ulength = resolve -> length;
				}
				if (blockmail -> tracker) {
					if (! url) {
						url = cont + start;
						ulength = ulen;
					}
					tracker_fill (blockmail -> tracker, blockmail, & url, & ulength);
				}
				if (url) {
					if (ulength > 0)
						xmlBufferAdd (block -> out, url, ulength);
					lstore = end;
					changed = true;
				}
			}
			state = ST_INITIAL;
		}
	}
	if (changed) {
		if (lstore < len)
			xmlBufferAdd (block -> out, cont + lstore, len - lstore);
		SWAP (block);
	}
	if (scratch)
		purl_free (scratch);
	return true;
}/*}}}*/
static bool_t
modify_urls_updated (blockmail_t *blockmail, receiver_t *rec, block_t *block, protect_t *protect, bool_t ishtml, record_t *record) /*{{{*/
{
	int		n;
	int		len;
	const xmlChar	*cont;
	int		lstore;
	int		state;
	char		ch, quote;
	int		start, end;
	int		mask;
	int		clen;
	bool_t		changed;
	purl_t		*scratch;
	
	scratch = NULL;
	xmlBufferEmpty (block -> out);
	len = xmlBufferLength (block -> in);
	cont = xmlBufferContent (block -> in);
	lstore = 0;
	state = ST_INITIAL;
	quote = '\0';
	if (ishtml) {
		mask = 2;
	} else {
		mask = 1;
	}
	start = -1;
	end = -1;
	changed = false;
	for (n = 0; n <= len; ) {
		if (n < len) {
			clen = xmlCharLength (cont[n]);
			if (protect) {
				if (n >= protect -> start) {
					if (n < protect -> end)
						n += clen;
					else 
						protect = protect -> next;
					continue;
				}
			}
			if ((clen > 1) || isascii ((char) cont[n])) {
				ch = clen == 1 ? (char) cont[n] : '\0';
				switch (state) {
				case ST_INITIAL:
					if (ishtml) {
						if (ch == '<') {
							state = 100;
						}
					} else if (strchr ("hm", tolower (ch))) {
						if (tolower (ch) == 'h') {
							state = 1;
						} else {
							state = 31;
						}
						start = n;
					}
					break;
# define	CHK(ccc)	do { if ((ccc) == ch) ++state; else state = ST_INITIAL; } while (0)
# define	CCHK(ccc)	do { if ((ccc) == tolower (ch)) ++state; else state = ST_INITIAL; } while (0)
				/* plain: http:// and https:// */
				case 1:		CCHK ('t');	break;
				case 2:		CCHK ('t');	break;
				case 3:		CCHK ('p');	break;
				case 4:
					++state;
					if (tolower (ch) == 's')
						break;
					/* Fall through . . . */
				case 5:		CHK (':');	break;
				case 6:		CHK ('/');	break;
				case 7:
					if (ch == '/')
						state = ST_PLAIN_START_FOUND;
					else
						state = ST_INITIAL;
					break;
				/* plain: mailto: */
				case 31:	CCHK ('a');	break;
				case 32:	CCHK ('i');	break;
				case 33:	CCHK ('l');	break;
				case 34:	CCHK ('t');	break;
				case 35:	CCHK ('o');	break;
				case 36:	CHK (':');	break;
				case 37:
					state = ST_PLAIN_START_FOUND;
					break;
				/* HTML */
# define	HCHK(ccc)	do { if ((ccc) == tolower (ch)) ++state; else if ('>' == ch) state = ST_INITIAL; else state = 100; } while (0)
				case 100:	HCHK ('h');	break;
				case 101:	HCHK ('r');	break;
				case 102:	HCHK ('e');	break;
				case 103:	HCHK ('f');	break;
# undef		HCHK						
				case 104:	CHK ('=');	break;
				case 105:
					if ((ch == '"') || (ch == '\'')) {
						quote = ch;
						state = ST_QUOTED_START_FOUND;
						start = n + 1;
					} else {
						state = ST_PLAIN_START_FOUND;
						start = n;
					}
					break;
				case ST_PLAIN_START_FOUND:
					if (isspace (ch) || (ch == '>')) {
						end = n;
						state = ST_END_FOUND;
					}
					break;
				case ST_QUOTED_START_FOUND:
					if (isspace (ch) || (ch == quote)) {
						end = n;
						state = ST_END_FOUND;
					}
					break;
				default:
					log_out (blockmail -> lg, LV_ERROR, "modify_urls: invalid state %d at position %d", state, n);
					state = ST_INITIAL;
					break;
				}
# undef		CHK
# undef		CCHK					
			} else {
				if (state == ST_PLAIN_START_FOUND) {
					end = n;
					state = ST_END_FOUND;
				} else
					state = ST_INITIAL;
			}
			n += clen;
		} else {
			if (state == ST_PLAIN_START_FOUND) {
				end = n;
				state = ST_END_FOUND;
			}
			++n;
		}
		if (state == ST_END_FOUND) {
			int	ulen, m;
			url_t	*match;
			int	first_orig = -1;

			ulen = end - start;
			for (m = 0, match = NULL; m < blockmail -> url_count; ++m) {
				if (url_match (blockmail -> url[m], cont + start, ulen)) {
					match = blockmail -> url[m];
					if (blockmail -> url[m] -> usage & mask)
						break;
				}
				if ((first_orig == -1) && blockmail -> url[m] -> orig) {
					first_orig = m;
				}
			}
			if (lstore < start) {
				xmlBufferAdd (block -> out, cont + lstore, start - lstore);
				lstore = start;
			}
			if ((match == NULL) && (first_orig != -1)) {
				for (m = first_orig; m < blockmail -> url_count; ++m) {
					if (url_match_original (blockmail -> url[m], cont + start, ulen)) {
						match = blockmail -> url[m];
						if (blockmail -> url[m] -> usage & mask)
							break;
					}
				}
			}
			if (blockmail -> anon) {
				if (! blockmail -> anon_preserve_links) {
					if ((m == blockmail -> url_count) || blockmail -> url[m] -> admin_link) {
						xmlBufferAdd (block -> out, (const xmlChar *) "#", 1);
						lstore = end;
						changed = true;
					} else if (url_is_personal (cont + start, ulen)) {
						if (! scratch)
							scratch = purl_alloc (NULL);
						if (scratch && purl_parsen (scratch, cont + start, ulen)) {
							const xchar_t	*rplc;
							int		rlen;
							rplc_t		replacer = { blockmail, rec };
							
							if ((rplc = purl_build (scratch, NULL, & rlen, replace_anon_hashtags, & replacer)) && rlen)
								xmlBufferAdd (block -> out, (const xmlChar *) rplc, rlen);
							lstore = end;
							changed = true;
						}
					}
				}
			} else if (m < blockmail -> url_count) {
				mkautourl (blockmail, rec, block, blockmail -> url[m], record);
				lstore = end;
				changed = true;
			} else if (match && match -> resolved) {
				const xmlChar	*url = NULL;
				int		ulength = -1;
				buffer_t	*resolve;
				
				if (resolve = link_resolve_get (match -> resolved, blockmail, block, match, rec, record)) {
					url = resolve -> buffer;
					ulength = resolve -> length;
				}
				if (blockmail -> tracker) {
					if (! url) {
						url = cont + start;
						ulength = ulen;
					}
					tracker_fill (blockmail -> tracker, blockmail, & url, & ulength);
				}
				if (url) {
					if (ulength > 0)
						xmlBufferAdd (block -> out, url, ulength);
					lstore = end;
					changed = true;
				}
			}
			state = ST_INITIAL;
		}
	}
	if (changed) {
		if (lstore < len)
			xmlBufferAdd (block -> out, cont + lstore, len - lstore);
		SWAP (block);
	}
	if (scratch)
		purl_free (scratch);
	return true;
}/*}}}*/
bool_t
modify_urls (blockmail_t *blockmail, receiver_t *rec, block_t *block, protect_t *protect, bool_t ishtml, record_t *record) /*{{{*/
{
	return (blockmail -> use_new_url_modification ? modify_urls_updated : modify_urls_original) (blockmail, rec, block, protect, ishtml, record);
}/*}}}*/
static inline const byte_t *
lskip (const byte_t *line, int *linelen) /*{{{*/
{
	while ((*linelen > 0) && isspace (*line))
		++line, --*linelen;
	return line;
}/*}}}*/
static inline void
ltrim (const byte_t *line, int *linelen) /*{{{*/
{
	while ((*linelen > 0) && isspace (line[*linelen - 1]))
		--*linelen;
}/*}}}*/
static char *
find_sender_in_from_header (const byte_t *line, int linelen) /*{{{*/
{
	int	resultpos = -1;
	int	resultlen = -1;
	char	*rc;
	int	n;
	bool_t	bracket, quote;

	line = lskip (line, & linelen);
	if ((linelen > 0) && (line[0] == '(')) {
		++line;
		--linelen;
		while ((linelen > 0) && (*line != ')'))
			++line, --linelen;
		if (linelen > 0) {
			++line, --linelen;
			line = lskip (line, & linelen);
		}
	}
	if ((linelen > 0) && (line[linelen - 1] == ')')) {
		--linelen;
		while ((linelen > 0) && (line[linelen - 1] != '('))
			--linelen;
		if (linelen > 0) {
			--linelen;
			ltrim (line, & linelen);
		}
	}
	for (n = 0, bracket = false, quote = false; n < linelen; ++n) {
		if (bracket) {
			if (line[n] == '>')
				break;
			++resultlen;
		} else if (quote) {
			if (line[n] == '"')
				quote = false;
		} else if (line[n] == '<') {
			bracket = true;
			resultpos = n + 1;
			resultlen = 0;
		} else if (line[n] == '"') {
			quote = true;
		}
	}
	if (resultpos == -1) {
		ltrim (line, & linelen);
		resultpos = 0;
		resultlen = linelen;
	}
	if (rc = malloc (resultlen + 1)) {
		memcpy (rc, line + resultpos, resultlen);
		rc[resultlen] = '\0';
	}
	return rc;
}/*}}}*/
static void
update_sender_in_header (block_t *block, const char *sender) /*{{{*/
{
	const xmlChar	*content = xmlBufferContent (block -> in);
	int		length = xmlBufferLength (block -> in);
	int		linepos = 0;
	const xmlChar	*inptr = content;
	int		inlen = length;
	const xmlChar	*current;
	int		clen;
								
	while (inlen > 0) {
		if ((linepos == 0) && (*inptr == 'S')) {
			++inptr, --inlen;
			if ((inlen > 0) && (*inptr == '<')) {
				++inptr, --inlen;
				current = inptr;
				while ((inlen > 0) && (*inptr != '>') && (*inptr != '\n'))
					++inptr, --inlen;
				if ((inlen > 0) && (*inptr == '>')) {
					clen = inptr - current;
					if ((strlen (sender) != clen) || memcmp (inptr, sender, clen)) {
						xmlBufferAdd (block -> out, content, current - content);
						xmlBufferCCat (block -> out, sender);
						xmlBufferAdd (block -> out, inptr, inlen);
						SWAP (block);
					}
				}
			}
			break;
		} else {
			if (*inptr == '\n')
				linepos = 0;
			else
				++linepos;
			++inptr, --inlen;
		}
	}
}/*}}}*/
static bool_t
revalidate_mfrom (blockmail_t *blockmail, block_t *block) /*{{{*/
{
	bool_t		rc = false;

	if ((block -> revalidation.source = buffer_realloc (block -> revalidation.source, xmlBufferLength (block -> in) + 1)) &&
	    (block -> revalidation.target = buffer_realloc (block -> revalidation.target, buffer_length (block -> revalidation.source) + 128)) &&
	    buffer_set (block -> revalidation.source, xmlBufferContent (block -> in), xmlBufferLength (block -> in)) &&
	    flatten_header (block -> revalidation.target, block -> revalidation.source, true)) {
		const byte_t	*ptr = buffer_content (block -> revalidation.target);
		int		len = buffer_length (block -> revalidation.target);
		const byte_t	*line;
		int		linelen;
		char		*sender;

		rc = true;
		while (len > 0) {
			line = ptr;
			while ((len > 0) && (*ptr != '\n'))
				++ptr, --len;
			linelen = ptr - line;
			if (len > 0)
				++ptr, --len;
			if ((linelen > 5) && (! strncasecmp ((const char *) line, "from:", 5))) {
				line += 5;
				linelen -= 5;
				line = lskip (line, & linelen);
				if (sender = find_sender_in_from_header (line, linelen)) {
					if (*sender && spf_is_valid (blockmail -> spf, sender)) {
						update_sender_in_header (block, sender);
					}
					free (sender);
				} else
					rc = false;
				break;
			}
		}
	}
	return rc;
}/*}}}*/
static bool_t
collect_links (blockmail_t *blockmail, block_t *block, links_t *links) /*{{{*/
{
	bool_t		rc;
	int		n, clen;
	int		len;
	const xmlChar	*cont;
	int		start;
	
	rc = true;
	len = xmlBufferLength (block -> in);
	cont = xmlBufferContent (block -> in);
	for (n = 0; rc && (n < len); ) {
		clen = xmlCharLength (cont[n]);
		if ((clen == 1) && (cont[n] == 'h')) {
			start = n;
			++n;
			if ((n + 3 < len) && (cont[n] == 't') && (cont[n + 1] == 't') && (cont[n + 2] == 'p')) {
				n += 3;
				if ((n + 1 < len) && (cont[n] == 's'))
					++n;
				if ((n + 3 < len) && (cont[n] == ':') && (cont[n + 1] == '/') && (cont[n + 2] == '/')) {
					n += 3;
					while ((n < len) && (xmlCharLength (cont[n]) == 1) &&
					       (cont[n] != '"') && (cont[n] != '<') && (cont[n] != '>') &&
					       (! isspace (cont[n])))
						++n;
					rc = links_nadd (links, (const char *) (cont + start), n - start);
				}
			}
		} else
			n += clen;
	}
	return rc;
}/*}}}*/
static int
find_top (const xmlChar *cont, int len) /*{{{*/
{
	int		pos;
	int		state;
	int		n;
	int		clen;
	unsigned char	ch;

	for (pos = -1, state = 0, n = 0; (n < len) && (pos == -1); ) {
		clen = xmlCharLength (cont[n]);
		if (clen > 1)
			state = 0;
		else {
			ch = cont[n];

			switch (state) {
			case 0:
				if (ch == '<')
					state = 1;
				break;
			case 1:
				if ((ch == 'b') || (ch == 'B'))
					state = 2;
				else if (ch == '>')
					state = 0;
				else if (! isspace (ch))
					state = 100;
				break;
			case 2:
				if ((ch == 'o') || (ch == 'O'))
					state = 3;
				else if (ch == '>')
					state = 0;
				else
					state = 100;
				break;
			case 3:
				if ((ch == 'd') || (ch == 'D'))
					state = 4;
				else if (ch == '>')
					state = 0;
				else
					state = 100;
				break;
			case 4:
				if ((ch == 'y') || (ch == 'Y'))
					state = 5;
				else if (ch == '>')
					state = 0;
				else
					state = 100;
				break;
			case 5:
				if (ch == '>') {
					pos = n + clen;
					state = 0;
				} else if (isspace (ch))
					state = 6;
				else
					state = 100;
				break;
			case 6:
				if (ch == '>') {
					pos = n + clen;
					state = 0;
				}
# ifdef		STRICT				
				else if (ch == '"')
					state = 7;
				break;
			case 7:
				if (ch == '"')
					state = 6;
# endif		/* STRICT */
				break;
			case 100:
				if (ch == '>')
					state = 0;
				break;
			}
		}
		n += clen;
	}
	return pos;
}/*}}}*/
static int
find_bottom (const xmlChar *cont, int len) /*{{{*/
{
	int	pos;
	int	last;
	int	m;
	int	bclen;
	
	for (pos = -1, last = len, m = len - 1; (m >= 0) && (pos == -1); ) {
		bclen = xmlCharLength (cont[m]);
		if ((bclen == 1) && (cont[m] == '<')) {
			int		n;
			int		state;
			int		clen;
			unsigned char	ch;

			for (n = m + bclen, state = 1; (n < last) && (state > 0) && (state != 99); ) {
				clen = xmlCharLength (cont[n]);
				if (clen != 1)
					state = 0;
				else {
					ch = cont[n];
					switch (state) {
					case 1:
						if (ch == '/')
							state = 2;
						else if (! isspace (ch))
							state = 0;
						break;
					case 2:
						if ((ch == 'b') || (ch == 'B'))
							state = 3;
						else if (! isspace (ch))
							state = 0;
						break;
					case 3:
						if ((ch == 'o') || (ch == 'O'))
							state = 4;
						else
							state = 0;
						break;
					case 4:
						if ((ch == 'd') || (ch == 'D'))
							state = 5;
						else
							state = 0;
						break;
					case 5:
						if ((ch == 'y') || (ch == 'Y'))
							state = 6;
						else
							state = 0;
						break;
					case 6:
						if ((ch == '>') || isspace (ch))
							state = 99;
						else
							state = 0;
						break;
					}
				}
				n += clen;
			}
			if (state == 99)
				pos = m;
		} else if ((bclen == 1) && (cont[m] == '>'))
			last = m + bclen;
		m -= bclen;
	}
	return pos;
}/*}}}*/
static bool_t
add_onepixellog_image (blockmail_t *blockmail, receiver_t *rec, block_t *block, opl_t opl) /*{{{*/
{
	const char	*opx;
	
	if (opx = mkonepixellogurl (blockmail, rec)) {
		int		pos;
		int		len;
		const xmlChar	*cont;
		
		pos = -1;
		len = xmlBufferLength (block -> in);
		cont = xmlBufferContent (block -> in);
		switch (opl) {
		case OPL_None:
			break;
		case OPL_Top:
			pos = find_top (cont, len);
			if (pos == -1)
				pos = 0;
			break;
		case OPL_Bottom:
			pos = find_bottom (cont, len);
			if (pos == -1)
				pos = len;
			break;
		}
		if (pos != -1) {
			xmlBufferEmpty (block -> out);
			if (pos > 0)
				xmlBufferAdd (block -> out, cont, pos);
			if (blockmail -> onepix_template) {
				map_t		*local = string_map_setup ();
				xmlBufferPtr	temp;
				
				string_map_addss (local, "link", opx);
				if (temp = string_mapn (blockmail -> onepix_template, local, rec -> smap, blockmail -> smap, NULL)) {
					xmlBufferAdd (block -> out, xmlBufferContent (temp), xmlBufferLength (temp));
					xmlBufferFree (temp);
				}
			} else {
				const xmlChar	lprefix[] = "<img src=\"";
				const xmlChar	lpostfix[] = "\" alt=\"\" border=\"0\" height=\"1\" width=\"1\"/>";
			
				xmlBufferAdd (block -> out, lprefix, sizeof (lprefix) - 1);
				xmlBufferCCat (block -> out, opx);
				xmlBufferAdd (block -> out, lpostfix, sizeof (lpostfix) - 1);
			}
			if (pos < len)
				xmlBufferAdd (block -> out, cont + pos, len - pos);
			SWAP (block);
		}
	}
	return true;
}/*}}}*/
static bool_t
convert_entities (blockmail_t *blockmail, block_t *block) /*{{{*/
{
	xmlBufferEmpty (block -> out);
	entity_replace (block -> in, block -> out, false);
	SWAP (block);
	return true;
}/*}}}*/
static bool_t
add_vip_block (blockmail_t *blockmail, block_t *block) /*{{{*/
{
	bool_t		rc;
	int             len;
	int             pos;
	const xmlChar   *cont;
	
	rc = true;
	len = xmlBufferLength (block -> in);
	cont = xmlBufferContent (block -> in);
	pos = find_top (cont, len);
	if (pos == -1)
		pos = 0;
	xmlBufferEmpty (block -> out);
	if (pos > 0)
		xmlBufferAdd (block -> out, cont, pos);
	xmlBufferAdd (block -> out, xmlBufferContent (blockmail -> vip), xmlBufferLength (blockmail -> vip));
	if (pos < len)
		xmlBufferAdd (block -> out, cont + pos, len - pos);
	SWAP (block);
	return rc;
}/*}}}*/
static
# ifdef		__OPTIMIZE__
inline
# endif		/* __OPTIMIZE__ */
bool_t
islink (const xmlChar *str, int len) /*{{{*/
{
	int	n, state, clen;
	
	for (n = 0, state = 1; (n < len) && state; ) {
		clen = xmlCharLength (str[n]);
		if (clen != 1)
			return false;
		switch (state) {
		default: /* should NEVER happen */
			return false;
		case 1:	/* check for http:// https:// and mailto: */
			if ((str[n] == 'h') || (str[n] == 'H'))
				++state;
			else if ((str[n] == 'm') || (str[n] == 'M'))
				state = 100;
			else
				return false;
			break;
		case 2:
			if ((str[n] == 't') || (str[n] == 'T'))
				++state;
			else
				return false;
			break;
		case 3:
			if ((str[n] == 't') || (str[n] == 'T'))
				++state;
			else
				return false;
			break;
		case 4:
			if ((str[n] == 'p') || (str[n] == 'P'))
				++state;
			else
				return false;
			break;
		case 5:
			if ((str[n] == 's') || (str[n] == 'S'))
				++state;
			else if (str[n] == ':')
				state += 2;
			else
				return false;
			break;
		case 6:
			if (str[n] == ':')
				++state;
			else
				return false;
			break;
		case 7:
			if (str[n] == '/')
				++state;
			else
				return false;
			break;
		case 8:
			if (str[n] == '/')
				state = 0;
			else
				return false;
			break;
		case 100:
			if ((str[n] == 'a') || (str[n] == 'A'))
				++state;
			else
				return false;
			break;
		case 101:
			if ((str[n] == 'i') || (str[n] == 'I'))
				++state;
			else
				return false;
			break;
		case 102:
			if ((str[n] == 'l') || (str[n] == 'L'))
				++state;
			else
				return false;
			break;
		case 103:
			if ((str[n] == 't') || (str[n] == 'T'))
				++state;
			else
				return false;
			break;
		case 104:
			if ((str[n] == 'o') || (str[n] == 'O'))
				++state;
			else
				return false;
			break;
		case 105:
			if (str[n] == ':')
				state = 0;
			else
				return false;
			break;
		}
		n += clen;
	}
	return (! state) ? true : false;
}/*}}}*/
static bool_t
modify_linelength (blockmail_t *blockmail, block_t *block, blockspec_t *bspec) /*{{{*/
{
# define	DOIT_NONE	(0)
# define	DOIT_RESET	(1 << 0)
# define	DOIT_NEWLINE	(1 << 1)
# define	DOIT_IGNORE	(1 << 2)
# define	DOIT_SKIP	(1 << 3)	
	int		n;
	int		len;
	const xmlChar	*cont;
	int		spos, slen;
	int		space, dash;
	int		spchr;
	int		inspace, spacecount;
	int		llen, wordstart;
	int		doit;
	int		skipcount;
	bool_t		changed;

	xmlBufferEmpty (block -> out);
	len = xmlBufferLength (block -> in);
	cont = xmlBufferContent (block -> in);
	spos = 0;
	space = -1;
	dash = -1;
	spchr = -1;
	inspace = 0;
	spacecount = 0;
	llen = 0;
	wordstart = 0;
	doit = DOIT_NONE;
	skipcount = 0;
	changed = false;
	for (n = 0; n < len; ) {
		if ((cont[n] == '\r') || (cont[n] == '\n')) {
			doit = DOIT_RESET;
			if (inspace && (spchr + inspace == llen)) {
				n = space;
				doit |= DOIT_NEWLINE | DOIT_IGNORE | DOIT_SKIP;
				skipcount = inspace + 1;
			}
		} else {
			if ((cont[n] == ' ') || (cont[n] == '\t')) {
				if (! inspace++) {
					space = n;
					spchr = llen;
				}
				spacecount = inspace;
			} else {
				if (inspace) {
					inspace = 0;
					wordstart = n;
				}
				if ((cont[n] == '-') && (llen > 2) &&
				    ((spchr == -1) || (llen - spchr > 2)) &&
				    (! islink (cont + wordstart, n - wordstart))) {
					dash = n;
				}
			}
			if (++llen >= bspec -> linelength)
				if ((space != -1) || (dash != -1)) {
					if (space > dash) {
						if (! inspace) {
							n = space;
							doit = DOIT_RESET | DOIT_NEWLINE | DOIT_IGNORE | DOIT_SKIP;
							skipcount = spacecount;
						}
					} else {
						n = dash;
						doit = DOIT_RESET | DOIT_NEWLINE;
					}
				}
		}
		if (! (doit & DOIT_IGNORE))
			n += xmlCharLength (cont[n]);
		if (doit) {
			slen = n - spos;
			if (slen > 0)
				xmlBufferAdd (block -> out, cont + spos, slen);
			if (doit & DOIT_NEWLINE) {
				xmlBufferAdd (block -> out, bspec -> linesep, bspec -> seplength);
				changed = true;
			}
			if (doit & DOIT_SKIP) {
				while ((skipcount-- > 0) && (n < len))
					n += xmlCharLength (cont[n]);
				changed = true;
			}
			spos = n;
			space = -1;
			dash = -1;
			spchr = -1;
			inspace = 0;
			spacecount = 0;
 			llen = 0;
			wordstart = n;
			doit = DOIT_NONE;
		}
	}
	if (changed) {
		if (spos < len)
			xmlBufferAdd (block -> out, cont + spos, len - spos);
		SWAP (block);
	}
	return true;
# undef		DOIT_NONE
# undef		DOIT_RESET
# undef		DOIT_NEWLINE
# undef		DOIT_IGNORE
# undef		DOIT_SKIP
}/*}}}*/
bool_t
modify_header (blockmail_t *blockmail, block_t *header) /*{{{*/
{
	bool_t	rc = (header -> tid == TID_EMail_Head) ? true : false;
	
	if (rc && blockmail -> revalidate_mfrom) {
		rc = revalidate_mfrom (blockmail, header);
	}
	return rc;
}/*}}}*/
bool_t
modify_output (blockmail_t *blockmail, receiver_t *rec, block_t *block, blockspec_t *bspec, links_t *links) /*{{{*/
{
	bool_t	rc;
	
	rc = true;
	if (rc &&
	    (block -> tid == TID_EMail_HTML) &&
	    links) {
		rc = collect_links (blockmail, block, links);
	}
	if (rc &&
	    (! blockmail -> anon) &&
	    (block -> tid == TID_EMail_HTML) &&
	    bspec &&
	    (bspec -> opl != OPL_None)) {
		rc = add_onepixellog_image (blockmail, rec, block, bspec -> opl);
	}
	if (rc &&
	    (block -> tid == TID_EMail_HTML) &&
	    blockmail -> convert_to_entities) {
		rc = convert_entities (blockmail, block);
	}
	if (rc &&
	    blockmail -> vip &&
	    (block -> tid == TID_EMail_HTML) &&
	    islower (rec -> user_type) &&
	    (tolower (blockmail -> status_field) == rec -> user_type)) {
		rc = add_vip_block (blockmail, block);
	}
	if (rc &&
	    (block -> tid == TID_EMail_Text) &&
	    bspec &&
	    (bspec -> linelength > 0) &&
	    bspec -> linesep) {
		rc = modify_linelength (blockmail, block, bspec);
	}
	return rc;
}/*}}}*/
