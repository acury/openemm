#!/usr/bin/env python3
####################################################################################################################################################################################################################################################################
#                                                                                                                                                                                                                                                                  #
#                                                                                                                                                                                                                                                                  #
#        Copyright (C) 2022 AGNITAS AG (https://www.agnitas.org)                                                                                                                                                                                                   #
#                                                                                                                                                                                                                                                                  #
#        This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.    #
#        This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.           #
#        You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.                                                                                                            #
#                                                                                                                                                                                                                                                                  #
####################################################################################################################################################################################################################################################################
#
from	__future__ import annotations
import	argparse, logging, os, time, socket, re
from	dataclasses import dataclass
from	typing import Optional
from	typing import Dict, List, NamedTuple, Set
from	agn3.db import DBIgnore, DB
from	agn3.definitions import base, fqdn, syscfg
from	agn3.exceptions import error
from	agn3.ignore import Ignore
from	agn3.io import create_path, which
from	agn3.log import log_limit
from	agn3.mta import MTA
from	agn3.parser import Unit
from	agn3.runtime import Runtime
from	agn3.stream import Stream
from	agn3.tools import listsplit, silent_call, escape
#
logger = logging.getLogger (__name__)
#
class BavUpdate (Runtime): 
	unit = Unit ()
	default_filter_domain = 'localhost'
	config_filename = os.path.join (base, 'var', 'lib', 'bav.conf')
	control_sendmail = os.path.join (base, 'bin', 'smctrl')
	restart_sendmail = os.path.join (base, 'var', 'run', 'sendmail-control.sh')
	sendmail_base = '/etc/mail'
	valid_domain_pattern = re.compile ('^[0-9a-z][0-9a-z_+-]*(\\.[0-9a-z][0-9a-z_+-]*)+$', re.IGNORECASE)
	def supports (self, option: str) -> bool:
		return option not in ('dryrun', 'parameter')
	
	def setup (self) -> None:
		self.delay = BavUpdate.unit.parse ('3m')
		self.fqdn = socket.getfqdn ().lower ()
		if not self.fqdn:
			self.fqdn = fqdn
	
	def add_arguments (self, parser: argparse.ArgumentParser) -> None:
		parser.add_argument ('-D', '--delay', action = 'store', type = BavUpdate.unit.parse, default = self.delay, help = 'delay between each reread of database content')
	
	def use_arguments (self, args: argparse.Namespace) -> None:
		self.delay = args.delay

	def prepare (self) -> None:
		self.filter_domain = syscfg.get ('filter-name', BavUpdate.default_filter_domain)
		if self.filter_domain == BavUpdate.default_filter_domain:
			with DBIgnore (), DB () as db:
				rq = db.querys ('SELECT mailloop_domain FROM company_tbl WHERE company_id = 1')
				if rq is not None and self.valid_domain (rq.mailloop_domain):
					self.filter_domain = rq.mailloop_domain
		self.mta = MTA ()
		self.domains: List[str] = []
		self.mtdom: Dict[str, int] = {}
		self.prefixes = ['aml_', 'reply_']
		self.prefix_pattern = re.compile ('^({p})[0-9]+@'.format (p = '|'.join ([re.escape (_p) for _p in self.prefixes])), re.IGNORECASE)
		self.reserved_local_parts = {'fbl'}
		self.last = ''
		self.read_mailertable ()

	def executor (self) -> bool:
		while self.running:
			self.update ()
			n = self.delay
			while n > 0 and self.running:
				time.sleep (1)
				n -= 1
		return True
	
	def file_reader (self, fname: str) -> List[str]:
		with open (fname, errors = 'backslashreplace') as fd:
			return [line.rstrip ('\r\n') for line in fd if not line[0] in '\n#']
	
	def valid_domain (self, domain: Optional[str]) -> bool:
		return domain is not None and self.valid_domain_pattern.match (domain) is not None

	class Domain (NamedTuple):
		name: str
		value: str
	@dataclass
	class Filecontent:
		path: Optional[str]
		content: List[BavUpdate.Domain]
		modified: bool
		hash: Optional[str]
	@dataclass
	class RID:
		rid: int
		domain: str
	postfix_database_extension_mapping = {
		'hash':	'db'
	}
	def read_mailertable (self, new_domains: Optional[Dict[str, BavUpdate.RID]] = None) -> None:
		self.domains.clear ()
		self.mtdom.clear ()
		if self.mta.mta == 'postfix':
			def find (key: str, default_value: str) -> BavUpdate.Filecontent:
				rc = BavUpdate.Filecontent (path = None, content = [], modified = False, hash = None)
				with Ignore (KeyError):
					for element in self.mta.getlist (key):
						hash: Optional[str]
						path: str
						try:
							(hash, path) = element.split (':', 1)
						except ValueError:
							(hash, path) = (None, element)
						if path.startswith (base):
							if rc.path is None:
								rc.path = path
								rc.hash = hash
							if not os.path.isfile (path):
								create_path (os.path.dirname (path))
								open (path, 'w').close ()
								if hash is not None:
									self.mta.postfix_make (hash, path)
							elif rc.hash is not None:
								hash_path = '{path}.{extension}'.format (
									path = rc.path,
									extension = self.postfix_database_extension_mapping.get (rc.hash, rc.hash)
								)
								try:
									target_stat = os.stat (hash_path)
									source_stat = os.stat (path)
									if target_stat.st_mtime <= source_stat.st_mtime:
										raise error ('outdated hash file')
								except Exception as e:
									logger.info (f'force creating of {hash_path} due to {e}')
									self.mta.postfix_make (hash, path)
					if rc.path is not None:
						try:
							with open (rc.path) as fd:
								for line in (_l.strip () for _l in fd):
									try:
										(var, val) = [_v.strip () for _v in line.split (None, 1)]
									except ValueError:
										var = line
										val = default_value
										rc.modified = True
									#
									if self.valid_domain (var) and var not in [_c.name for _c in rc.content]:
										rc.content.append (BavUpdate.Domain (name = var, value = val))
									else:
										rc.modified = True
							logger.debug ('Read {length:,d} lines from {path}'.format (
								length = len (rc.content),
								path = rc.path
							))
						except OSError as e:
							logger.error (f'Failed to read {rc.path}: {e}')
					else:
						logger.warning (f'No path for postfix parameter "{key}" found')
				return rc
			def save (ct: BavUpdate.Filecontent) -> None:
				if ct.path is not None and (ct.modified or not os.path.isfile (ct.path)):
					try:
						with open (ct.path, 'w') as fd:
							if ct.content:
								fd.write ('\n'.join (['%s\t%s' % _c for _c in ct.content]) + '\n')
						logger.info ('Written {count:,d} lines to {path}'.format (
							count = len (ct.content),
							path = ct.path
						))
						if ct.hash is not None:
							self.mta.postfix_make (ct.hash, ct.path)
					except OSError as e:
						logger.error (f'Failed to save {ct.path}: {e}')
			#
			relay_default_value = 'dummy'
			relays = find ('relay_domains', relay_default_value)
			for d in relays.content:
				self.mtdom[d.name] = 0
			#
			def add_relay_domain (domain_to_add: str) -> None:
				if self.valid_domain (domain_to_add) and domain_to_add not in self.mtdom:
					relays.content.append (BavUpdate.Domain (name = domain_to_add, value = relay_default_value))
					relays.modified = True
					self.mtdom[domain_to_add] = 0
			#
			if new_domains:
				for domain in new_domains:
					add_relay_domain (domain)
			transport_default_value = 'mailloop:'
			transports = find ('transport_maps', transport_default_value)
			with DBIgnore (), DB () as db:
				for row in db.query ('SELECT mailloop_domain FROM company_tbl WHERE mailloop_domain IS NOT NULL AND status = :status', {'status': 'active'}):
					if self.valid_domain (row.mailloop_domain):
						add_relay_domain (row.mailloop_domain.strip ().lower ())
			transport_domains = set ([_c[0] for _c in transports.content])
			for d in relays.content[:]:
				if d.name not in transport_domains:
					transports.content.append (BavUpdate.Domain (name = d.name, value = transport_default_value))
					transports.modified = True
				self.mtdom[d.name] += 1
			save (relays)
			save (transports)
			if relays.modified or transports.modified:
				cmd = which ('smctrl')
				if cmd is not None:
					n = silent_call (cmd, 'service', 'reload')
					if n == 0:
						logger.info ('Reloaded')
					else:
						logger.error (f'Reloading failed: {n}')
			self.domains = [_c.name for _c in relays.content]
		else:
			try:
				for line in self.file_reader (os.path.join (self.sendmail_base, 'mailertable')):
					parts = line.split ()
					if len (parts) > 1 and not parts[0].startswith ('.') and parts[1].startswith ('procmail:'):
						self.domains.append (parts[0])
						self.mtdom[parts[0]] = 0
			except IOError as e:
				logger.error (f'Unable to read mailertable: {e}')
			try:
				for line in self.file_reader (os.path.join (self.sendmail_base, 'relay-domains')):
					if line in self.mtdom:
						self.mtdom[line] += 1
					else:
						logger.debug (f'We relay domain "{line}" without catching it in mailertable')
				for key in self.mtdom.keys ():
					if self.mtdom[key] == 0:
						logger.debug (f'We define domain "{key}" in mailertable, but do not relay it')
			except IOError as e:
				logger.error (f'Unable to read relay-domains: {e}')
	
	def read_mail_files (self) -> List[str]:
		rc: List[str] = []
		if self.fqdn:
			rc.append (f'@{self.fqdn}\taccept:rid=local')
		if self.mta.mta == 'sendmail':
			try:
				for line in self.file_reader (os.path.join (self.sendmail_base, 'local-host-names')):
					rc.append (f'@{line}\taccept:rid=local')
			except IOError as e:
				logger.error (f'Unable to read local-host-names: {e}')
			try:
				for line in self.file_reader (os.path.join (self.sendmail_base, 'virtusertable')):
					parts = line.split ()
					if len (parts) == 2:
						rc.append (f'{parts[0]}\taccept:rid=virt,fwd={parts[1]}')
			except IOError as e:
				logger.error (f'Unable to read virtusertable: {e}')
		return rc

	class Forward (NamedTuple):
		rid: int
		address: str
	def read_database (self) -> List[str]:
		rc: List[str] = []
		with DBIgnore (), DB () as db:
			company_list: List[int] = []
			new_domains: Dict[str, BavUpdate.RID] = {}
			forwards: List[BavUpdate.Forward] = []
			seen_domains: Set[str] = set ()
			accepted_forwards: Set[str] = set ()
			ctab: Dict[int, str] = {}
			#
			rc.append (f'fbl@{self.filter_domain}\taccept:rid=fbl')
			for domain in self.domains:
				if domain not in seen_domains:
					if domain != self.filter_domain:
						rc.append (f'fbl@{domain}\talias:fbl@{self.filter_domain}')
					seen_domains.add (domain)
			if self.filter_domain not in seen_domains:
				new_domains[self.filter_domain] = BavUpdate.RID (rid = 0, domain = self.filter_domain)
			seen_domains.add (self.filter_domain)
			#
			missing = []
			for row in db.query ('SELECT company_id, mailloop_domain FROM company_tbl WHERE status = :status', {'status': 'active'}):
				if self.valid_domain (row.mailloop_domain):
					ctab[row.company_id] = row.mailloop_domain
					if row.mailloop_domain not in seen_domains:
						rc.append (f'fbl@{row.mailloop_domain}\talias:fbl@{self.filter_domain}')
						if row.mailloop_domain not in self.mtdom and row.mailloop_domain.lower () != self.fqdn:
							new_domains[row.mailloop_domain] = BavUpdate.RID (rid = 0, domain = row.mailloop_domain)
						seen_domains.add (row.mailloop_domain)
				else:
					missing.append (row.company_id)
				company_list.append (row.company_id)
			if missing:
				logger.debug ('Missing (valid) mailloop_domain for companies {companies}'.format (
					companies = Stream (missing).sorted ().join (', ')
				))
			#
			seen_filter_addresses: Dict[str, str] = {}
			for row in db.query (
				'SELECT rid, shortname, company_id, filter_address, '
				'       forward_enable, forward, ar_enable, '
				'       subscribe_enable, mailinglist_id, form_id, timestamp, '
				'       spam_email, spam_required, spam_forward, '
				'       autoresponder_mailing_id, security_token '
				'FROM mailloop_tbl '
				'ORDER BY rid'
			):
				if row.company_id not in company_list or row.rid is None:
					if row.company_id not in company_list:
						logger.debug (f'{row}: ignore due to inactive company')
					elif row.rid is None:
						logger.error (f'{row}: ignore due to empty rid, should never happen!')
					continue
				#
				row_id = f'{row.rid} {row.shortname} [{row.company_id}]'
				domains: List[str] = [self.filter_domain]
				aliases: List[str] = []
				if row.filter_address is not None:
					for alias in listsplit (row.filter_address):
						if self.prefix_pattern.match (alias) is None:
							with Ignore (ValueError):
								(local_part, domain_part) = alias.split ('@', 1)
								if local_part.lower () in self.reserved_local_parts:
									logger.warning (f'{row_id}: local part of "{alias}" is reserved for internal use and therefor rejected')
								else:
									normalized_alias = '{local_part}@{domain_part}'.format (
										local_part = local_part,
										domain_part = domain_part.lower ()
									)
									if normalized_alias in seen_filter_addresses:
										logger.warning (f'{row_id}: already seen "{alias}" as "{normalized_alias}" before ({seen_filter_addresses[normalized_alias]})')
									else:
										seen_filter_addresses[normalized_alias] = row_id
										if domain_part not in domains:
											domains.append (domain_part)
											if domain_part not in self.mtdom and domain_part not in new_domains:
												new_domains[domain_part] = BavUpdate.RID (rid = row.rid, domain = domain_part)
										aliases.append (alias)
						else:
							log_limit (logger.info, f'{alias}: clashes with internal prefixes {self.prefixes}')
				#
				ar_enable = False
				if row.ar_enable and row.autoresponder_mailing_id:
					if not row.security_token:
						logger.error (f'{row_id}: Autoresponder has mailing id, but no security token, not used')
					else:
						ar_enable = True
				#
				try:
					cdomain = ctab[row.company_id]
					if cdomain not in domains:
						if cdomain in self.domains:
							domains.append (cdomain)
						else:
							logger.debug (f'{row_id}: company\'s domain "{cdomain}" not found in mailertable')
				except KeyError:
					logger.debug (f'{row_id}: no domain for company found, further processing')
				extra = [f'rid={row.rid}']
				if row.company_id:
					extra.append (f'cid={row.company_id}')
				if row.forward_enable and row.forward:
					forward = row.forward.strip ()
					if forward:
						extra.append (f'fwd={forward}')
						forwards.append (BavUpdate.Forward (rid = row.rid, address = forward))
				if row.spam_email:
					extra.append (f'spam_email={row.spam_email}')
				if row.spam_forward:
					forward = row.spam_forward.strip ()
					if forward:
						extra.append (f'spam_fwd={forward}')
				if row.spam_required:
					extra.append (f'spam_req={row.spam_required}')
				if ar_enable:
					extra.append (f'ar={row.rid}')
					if row.autoresponder_mailing_id:
						extra.append (f'armid={row.autoresponder_mailing_id}')
				if row.subscribe_enable and row.mailinglist_id and row.form_id:
					extra.append (f'sub={row.mailinglist_id}:{row.form_id}')
				for prefix in self.prefixes:
					rc.append ('{prefix}{rid}@{domain}\taccept:{extra}'.format (
						prefix = prefix,
						rid = row.rid,
						domain = self.filter_domain,
						extra =','.join ([escape (_e) for _e in extra])
					))
				for domain in domains:
					if domain != self.filter_domain:
						for prefix in self.prefixes:
							rc.append (f'{prefix}{row.rid}@{domain}\talias:{prefix}{row.rid}@{self.filter_domain}')
				if aliases:
					for alias in aliases:
						rc.append (f'{alias}\talias:{self.prefixes[0]}{row.rid}@{self.filter_domain}')
						accepted_forwards.add (alias)
			#
			for forward in forwards:
				with Ignore (ValueError):
					fdomain = (forward.address.split ('@', 1)[-1]).lower ()
					for domain in self.mtdom:
						if domain == fdomain and forward.address not in accepted_forwards:
							log_limit (logger.warning, f'{forward.rid}: using address "{forward.address}" with local handled domain "{domain}"')
					refuse = []
					for (domain, new_domain) in ((_d, _n) for (_d, _n) in new_domains.items () if _d == fdomain):
						log_limit (logger.warning, f'{new_domain.rid}: try to add new domain for already existing forward address "{forward.address}" in {forward.rid}, refused')
						refuse.append (domain)
					for domain in refuse:
						del new_domains[domain]
			#
			if new_domains:
				if self.mta.mta == 'sendmail':
					if os.access (BavUpdate.control_sendmail, os.X_OK):
						cmd = [BavUpdate.control_sendmail, 'add']
						for domain in new_domains:
							cmd.append (domain)
						logger.info (f'Found new domains, add them using {cmd}')
						silent_call (*cmd)
						if os.access (BavUpdate.restart_sendmail, os.X_OK):
							logger.info ('Restarting sendmail due to domain update')
							silent_call (BavUpdate.restart_sendmail)
						else:
							logger.warning (f'Missing {BavUpdate.restart_sendmail}, no restart of mta perfomed')
					else:
						logger.warning (f'Missing {BavUpdate.control_sendmail}, no new domains are added')
				self.read_mailertable (new_domains)
		return rc
	
	def rename_file (self, old_file: str, new_file: str) ->None:
		try:
			os.rename (old_file, new_file)
		except OSError as e:
			logger.error (f'Unable to rename {old_file} to {new_file}: {e}')
			try:
				os.unlink (old_file)
			except OSError as e:
				logger.warning (f'Failed to remove temp. file {old_file}: {e}')
			raise error ('rename_file')
	
	def update_file (self, path: str, content: str, last: str) -> None:
		if content != last:
			temp = '{path}.{pid:06d}'.format (path = path, pid = os.getpid ())
			try:
				with open (temp, 'w') as fd:
					fd.write (content)
				self.rename_file (temp, path)
			except IOError as e:
				logger.error (f'{temp}: failed to write temp.file: {e}')
				raise error (f'update_file.open {path}', e)

	def update_config_file (self, content: str) -> None:
		self.update_file (BavUpdate.config_filename, content, self.last)
		self.last = content

	def update (self) -> None:
		try:
			new = self.read_mail_files ()
			new += self.read_database ()
			self.update_config_file ('\n'.join (new) + '\n')
		except error as e:
			logger.exception (f'Update failed: {e}')

if __name__ == '__main__':
	BavUpdate.main ()
