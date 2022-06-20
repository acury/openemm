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
import	logging, argparse
import	os, time, re, errno, pickle
from	collections import defaultdict
from	functools import partial
from	datetime import datetime
from	dataclasses import dataclass, field
from	types import TracebackType
from	typing import Any, Callable, Final, Optional, Union
from	typing import DefaultDict, Dict, Iterator, List, Pattern, Set, TextIO, Tuple, Type
from	typing import cast
from	agn3.cache import Cache
from	agn3.db import DBIgnore, DB, Row
from	agn3.dbm import DBM
from	agn3.definitions import base, licence, syscfg, unique
from	agn3.email import EMail
from	agn3.emm.columns import Columns
from	agn3.emm.config import EMMCompany
from	agn3.emm.types import MediaType, UserStatus
from	agn3.exceptions import error
from	agn3.ignore import Ignore, Experimental
from	agn3.io import create_path, cstreamopen
from	agn3.log import log
from	agn3.parameter import Parameter
from	agn3.parser import Unit, ParseTimestamp, Line, Field, Lineparser, Tokenparser
from	agn3.plugin import Plugin, LoggingManager
from	agn3.runtime import Runtime
from	agn3.stream import Stream
from	agn3.template import Template
from	agn3.tools import atob, atoi
from	agn3.tracker import Key, Tracker
#
logger = logging.getLogger (__name__)
#
class UpdatePlugin (Plugin): #{{{
	plugin_version = '2.0'
#}}}
class Duplicate: #{{{
	__slots__ = ['name', 'expiration', 'path', 'dbs', 'write_count']
	def __init__ (self, name: str, expiration: int) -> None:
		self.name = name
		self.expiration = expiration
		self.path = os.path.join (base, 'var', 'run', f'duplicate-{name}')
		create_path (self.path)
		self.dbs: List[DBM] = []
		self.write_count = 0
	
	def __contains__ (self, line: str) -> bool:
		key = line.encode ('UTF-8')
		for db in self.dbs:
			if key in db:
				return True
		self.dbs[0][key] = b''
		self.write_count += 1
		if self.write_count % 1000 == 0:
			self.dbs[0].sync ()
		return False
	
	def __delitem__ (self, line: str) -> None:
		with Ignore (KeyError):
			del self.dbs[0][line.encode ('UTF-8')]
	
	def open (self) -> bool:
		self.close ()
		now = datetime.now ()
		today = now.toordinal ()
		filenames = (Stream.range (self.expiration + 1)
			.map (lambda n: now.fromordinal (today - n))
			.map (lambda d: '%04d%02d%02d.gdbm' % (d.year, d.month, d.day))
			.list ()
		)
		current = filenames[0]
		current_exists = False
		for filename in sorted (os.listdir (self.path), reverse = True):
			path = os.path.join (self.path, filename)
			if os.path.isfile (path):
				if filename in filenames:
					if filename == current:
						current_exists = True
						mode = 'wf'
					else:
						mode = 'ru'
					self.dbs.append (DBM (path, mode))
				else:
					logger.info ('Removing outdated file %s' % path)
					os.unlink (path)
		if not current_exists:
			path = os.path.join (self.path, current)
			self.dbs.insert (0, DBM (path, 'cf'))
		return bool (self.dbs)
	
	def close (self) -> None:
		if self.dbs:
			self.dbs[0].sync ()
			Stream (self.dbs).each (lambda d: d.close ())
		self.dbs.clear ()
#}}}
class Log: #{{{
	__slots__ = ['logpath', 'name', 'target']
	def __init__ (self, logpath: str, name: str) -> None:
		self.logpath = logpath
		self.name = name
		self.target = os.path.join (self.logpath, self.name)
		create_path (self.target)
	
	def unpack (self, s: str) -> Tuple[int, int]:
		(nr, seq) = (int (_v) for _v in s.split ('-'))
		return (nr, seq)
	
	valid = re.compile ('^[0-9]+-[0-9]+$')
	def __iter__ (self) -> Iterator[str]:
		return iter (Stream (os.listdir (self.target))
			.regexp (self.valid)
			.sorted (key = lambda f: self.unpack (f))
			.map (lambda f: os.path.join (self.target, f))
		)
	
	def __len__ (self) -> int:
		return sum (1 for _ in self)
	
	def add (self, path: str, max_lines: int = 10000) -> None:
		with open (path) as fd:
			fdw: Optional[TextIO] = None
			try:
				now = 0
				seq = 0
				cur = 0
				count = 0
				for line in fd:
					if fdw is None:
						ts = int (time.time ())
						if ts != now:
							now = ts
							seq = self.__next_sequence (now)
						while fdw is None:
							output = os.path.join (self.target, f'{now}-{seq}')
							seq += 1
							try:
								fno = os.open (output, os.O_CREAT |os.O_EXCL, 0o666)
								try:
									fdw = open (output, 'w')
								finally:
									os.close (fno)
							except OSError as e:
								if e.errno != errno.EEXIST:
									raise
						cur = 0
						count += 1
					fdw.write (line)
					cur += 1
					if cur >= max_lines:
						fdw.close ()
						fdw = None
			finally:
				if fdw is not None:
					fdw.close ()
			logger.info (f'Wrote {path} to {count:,d} chunks')
		try:
			os.unlink (path)
		except OSError as e:
			logger.error ('Failed to remove proccessed file: %s' % e)
	
	def __next_sequence (self, timestamp: int) -> int:
		seq = 0
		for filename in os.listdir (self.target):
			with Ignore (ValueError):
				(ts, sq) = self.unpack (filename)
				if ts == timestamp:
					seq = max (seq, sq + 1)
		return seq
#}}}
class Update: #{{{
	__slots__ = [
		'basename', 'failpath', 'current', 'pid', 'lineno', 'log',
		'check_for_duplicates', 'duplicate', 'tracker', 'tracker_age', 'tracker_expire',
		'plugin',
		'_mailings_to_company'
	]
	name = 'update'
	path = '/dev/null'
	timestamp_parser = ParseTimestamp ()
	def __init__ (self) -> None:
		directory = os.path.dirname (self.path)
		self.basename = os.path.basename (self.path).split ('.')[0]
		self.failpath = os.path.join (directory, f'{self.basename}.fail')
		self.current = 1
		self.pid = os.getpid ()
		self.lineno = 0
		self.log = Log (directory, self.name)
		self.check_for_duplicates = True
		self.duplicate: Optional[Duplicate] = None
		self.tracker = Tracker (os.path.join (base, 'var', 'run', f'update-{self.name}.track'))
		self.tracker_age: Unit.Parsable = None
		self.tracker_expire = 0
		self._mailings_to_company: Dict[int, Optional[int]] = {}

	def setup (self) -> None:
		self.plugin = UpdatePlugin (manager = LoggingManager)
		if self.check_for_duplicates:
			self.duplicate = Duplicate (name = self.name, expiration = 7)

	def done (self) -> None:
		self.tracker.close ()
		self.plugin.shutdown ()

	def step (self) -> None:
		if self.tracker_age is not None:
			day = datetime.now ().toordinal ()
			if self.tracker_expire != day:
				self.tracker_expire = day
				logger.info ('Start expiration')
				self.tracker.expire (created = self.tracker_age)
				logger.info ('Expiration done')

	def exists (self) -> bool:
		return os.access (self.path, os.F_OK) or bool (self.log)

	def rename_to_temp (self) -> str:
		tfname = self.path + '.%d.%d.%d' % (self.pid, time.time (), self.current)
		self.current += 1
		try:
			os.rename (self.path, tfname)
			logger.info ('Renamed %s to %s' % (self.path, tfname))
		except OSError as e:
			logger.error ('Unable to rename %s to %s: %s' % (self.path, tfname, e))
			raise error (f'failed to rename {self.path} to {tfname}', e)
		else:
			time.sleep (2)
		return tfname

	def __save (self, fname: str, line: str) -> bool:
		try:
			with open (fname, 'a') as fd:
				fd.write (line + '\n')
			return True
		except IOError as e:
			logger.error ('Failed to write to %s: %s' % (fname, e))
		return False

	def save_to_fail (self, line: str) -> bool:
		return self.__save (self.failpath, line)

	def save_to_log (self, line: str) -> bool:
		return self.__save (log.data_filename (self.basename), line)

	def update_prepare (self) -> bool:
		return True
	def update_finished (self) -> bool:
		return True
	def update_start (self, db: DB) -> bool:
		raise error ('Need to overwrite update_start in your subclass')
	def update_end (self, db: DB) -> bool:
		raise error ('Need to overwrite update_end in your subclass')
	def update_line (self, db: DB, line: str) -> bool:
		raise error ('Need to overwrite update_line in your subclass')

	def execute (self, is_active: Callable[[], bool], delay: Optional[int]) -> None:
		self.setup ()
		while is_active ():
			self.step ()
			if self.exists ():
				count = self.__fill_and_count_log ()
				if count > 0:
					if self.update_prepare ():
						with DBIgnore (), DB () as db:
							if not self.update (db, count, is_active):
								logger.info ('Update failed')
							else:
								logger.debug ('Update successed')
						if not self.update_finished ():
							logger.info ('Finished failed')
					else:
						logger.info ('Prepare failed')
			if delay is None:
				break
			else:
				n = delay
				while is_active () and n > 0:
					time.sleep (1)
					n -= 1
		self.done ()
	
	def __fill_and_count_log (self) -> int:
		if os.path.isfile (self.path):
			try:
				tfname = self.rename_to_temp ()
			except error as e:
				logger.error (f'Failed creating temp.file: {e}')
			else:
				try:
					count = cstreamopen (tfname).count ()
					logger.info (f'{tfname} with {count:,d} entries to process')
					self.log.add (tfname, 10000)
					logger.info (f'Added {tfname} to log')
				except IOError as e:
					logger.error (f'Unable to open {tfname}: {e}')
		count = 0
		for path in self.log:
			with Ignore (IOError):
				count += cstreamopen (path).count ()
		logger.info (f'Files with {count:,d} entries to process')
		return count

	def update (self, db: DB, count: int, is_active: Callable[[], bool]) -> bool:
		self.plugin ().start (self, db.cursor)
		rc = self.update_start (db)
		if rc:
			if self.duplicate is not None:
				self.duplicate.open ()
			self.lineno = 0
			for path in self.log:
				if os.path.isfile (path):
					do_remove = False
					new_path = '%s-%.3f' % (path, time.time ())
					try:
						os.rename (path, new_path)
						with open (new_path) as fd:
							do_remove = True
							for line in (_l.strip () for _l in fd):
								self.lineno += 1
								if self.duplicate is not None and line in self.duplicate:
									logger.debug (f'Ignore duplicate line: {line}')
								else:
									if not self.update_line (db, line):
										if not self.save_to_fail (line):
											do_remove = False
										rc = False
										if self.duplicate is not None:
											del self.duplicate[line]
									else:
										if not self.save_to_log (line):
											do_remove = False
						if do_remove:
							os.unlink (new_path)
					except (OSError, IOError) as e:
						logger.error ('Failed to process %s as %s: %s' % (path, new_path, e))
					logger.info (f'{self.name}: Now at line {self.lineno:,d} of {count:,d}')
					db.sync ()
				else:
					logger.warning ('%s: File %s vanished' % (self.name, path))
				if not rc or not is_active ():
					break
			if self.duplicate is not None:
				self.duplicate.close ()
		if not self.update_end (db):
			rc = False
		self.plugin ().end (self, db.cursor, rc)
		self.tracker.close ()
		return rc

	def mailing_to_company (self, db: DB, mailing_id: int) -> Optional[int]:
		try:
			return self._mailings_to_company[mailing_id]
		except KeyError:
			with db.request () as cursor:
				rq = cursor.querys (
					'SELECT company_id '
					'FROM mailing_tbl '
					'WHERE mailing_id = :mailing_id',
					{'mailing_id': mailing_id}
				)
				self._mailings_to_company[mailing_id] = rq.company_id if rq is not None else None
			return self._mailings_to_company[mailing_id]
#}}}

class Detail: #{{{
	Ignore = 0
	Internal = 100
	Success = 200
	SoftbounceOther = 400
	SoftbounceReceiver = 410
	SoftbounceMailbox = 420
	HardbounceOther = 510
	HardbounceReceiver = 511
	HardbounceSystem = 512
	HardbounceNotEncrypted = 513
	Softbounces = (SoftbounceOther, SoftbounceReceiver, SoftbounceMailbox)
	Hardbounces = (HardbounceOther, HardbounceReceiver, HardbounceSystem, HardbounceNotEncrypted)
	names = {
		Ignore:			'Ignore',
		Internal:		'Internal',
		Success:		'Success',
		SoftbounceOther:	'SB-Other',
		SoftbounceReceiver: 	'SB-Recv',
		SoftbounceMailbox:	'SB-MBox',
		HardbounceOther:	'HB-Other',
		HardbounceReceiver: 	'HB-Recv',
		HardbounceSystem:	'HB-Sys',
		HardbounceNotEncrypted:	'HB-Crypt'
	}
#}}}
class UpdateBounce (Update): #{{{
	__slots__ = [
		'mailing_map',
		'igcount', 'sucount', 'sbcount', 'hbcount', 'dupcount', 'blcount', 'rvcount', 'ccount',
		'translate', 'delayed_processing', 'cache', 'succeeded',
		'has_mailtrack', 'has_mailtrack_last_read', 'sys_encrypted_sending_enabled',
		'bounce_mark_duplicate'
	]
	name = 'bounce'
	path = os.path.join (base, 'log', 'extbounce.log')
	class Info: #{{{
		def __init__ (self, info: str) -> None:
			self.info = info
			self.map: Dict[str, str] = {}
			for elem in info.split ('\t'):
				parts = elem.split ('=', 1)
				if len (parts) == 2:
					self.map[parts[0]] = parts[1]
				elif len (parts) == 1:
					self.map['stat'] = elem

		def __str__ (self) -> str:
			return Stream (self.map.items ()).map (lambda kv: '%s="%s"' % kv).join (', ')
		__repr__ = __str__
		
		def __contains__ (self, var: str) -> bool:
			return var in self.map
		
		def __getitem__ (self, var: str) -> Optional[str]:
			return self.get (var)

		def get (self, var: str, default: Optional[str] = None) -> Optional[str]:
			try:
				return self.map[var]
			except KeyError:
				return default
	#}}}
	class Translate: #{{{
		default = {
			Detail.Ignore:			[4, 40, 500, 41, 42, 43, 44, 45, 46, 47, 5, 515, 52, 53, 54, 55, 56, 57],
			Detail.Success:			[2],
			Detail.SoftbounceOther:		[50, 532, 541, 544, 570, 574],
			Detail.SoftbounceReceiver:	[411, 412, 413, 414, 416, 417, 418, 510, 514, 571, 51],
			Detail.SoftbounceMailbox:	[421, 422, 520, 521, 522, 523, 524],
			Detail.HardbounceOther:		[531],
			Detail.HardbounceReceiver:	[511, 513, 516, 517, 572],
 			Detail.HardbounceSystem:	[512, 518]
		}
		class Pattern:
			control_pattern = re.compile ('^/(.*)/([a-z]*)$')
			def __init__ (self, data: str, debug: bool) -> None:
				try:
					self.data = Parameter (data).data
					self.valid = True
				except Exception as e:
					logger.warning ('Failed to parse input data %r: %s' % (data, e))
					if debug:
						print (f'Failed to parse input data {data!r}: {e}')
					self.data = {}
					self.valid = False
				self.debug = debug
				self.checks: Dict[str, Pattern[str]] = {}
				flags: Union[int, re.RegexFlag]
				for (key, value) in self.data.items ():
					try:
						if key == 'stat':
							pattern = value
							flags = re.IGNORECASE
						elif key == 'relay':
							if len (value) > 2 and value.startswith ('/') and value.endswith ('/'):
								pattern = value[1:-1]
							else:
								relays: List[str] = []
								for relay in value.split ('|'):
									if relay.endswith ('.'):
										relay = relay[:-1]
										post = ''
									else:
										post = '.*'
									if relay.startswith ('.'):
										relay = relay[1:]
										pre = '(.+\\.)?'
									else:
										pre = ''
									relays.append ('{pre}{relay}{post}'.format (
										pre = pre,
										relay = re.escape (relay),
										post = post
									))
								pattern = '^({relays})$'.format (relays = '|'.join (relays))
							flags = re.IGNORECASE
						else:
							flags = 0
							mtch = self.control_pattern.match (value)
							if mtch is not None:
								(pattern, opts) = mtch.groups ()
								for opt in opts:
									if opt == 'i':
										flags |= re.IGNORECASE
							else:
								pattern = value
						self.checks[key] = re.compile (pattern, flags)
						if self.debug:
							print ('\tTranslate: %s="%s" -> "%s"%s' % (key, value, pattern, ' (ignorecase)' if flags & re.IGNORECASE else ''))
					except re.error as e:
						logger.warning ('Failed to parse regex in %s="%s": %s' % (key, value, e))
						if self.debug:
							print (f'\tfailed to compile pattern {key}="{value}": {e}')
						self.valid = False

			def match (self, infos: UpdateBounce.Info) -> bool:
				for (key, pattern) in [(_k.lower (), _v) for (_k, _v) in self.checks.items ()]:
					value = infos[key]
					if value is None:
						return False
					if key == 'relay':
						value = (value.split (':')[0]).split ('[')[0]
					if pattern.search (value) is None:
						return False
				return True
		
		@dataclass
		class Element:
			rule_id: int
			dsn: int
			detail: int
			pattern: Optional[UpdateBounce.Translate.Pattern]

		def __init__ (self, debug: bool = False) -> None:
			self.debug = debug
			self.default_tab: Dict[int, List[UpdateBounce.Translate.Element]] = {}
			for (key, value) in self.default.items ():
				for dsn in value:
					self.default_tab[dsn] = [UpdateBounce.Translate.Element (rule_id = 0, dsn = dsn, detail = key, pattern = None)]
			self.tab: DefaultDict[int, Dict[int, List[UpdateBounce.Translate.Element]]] = defaultdict (dict)
		
		def clear (self) -> None:
			self.tab.clear ()

		def add (self, rule_id: int, company_id: int, dsn: int, detail: int, pattern_expr: Optional[str] = None) -> None:
			tab = self.tab[company_id]
			if pattern_expr is not None:
				if self.debug:
					print ('Pattern for company_id=%r for DSN=%r leading to Detail=%r using pattern %r' % (company_id, dsn, detail, pattern_expr))
				pattern: Optional[UpdateBounce.Translate.Pattern] = UpdateBounce.Translate.Pattern (pattern_expr, self.debug)
				if pattern is None or not pattern.valid:
					logger.error ('Invalid pattern "%s" for company %d found' % (pattern_expr, company_id))
					return
			else:
				pattern = None
			if dsn in tab:
				if pattern is not None:
					tab[dsn].insert (0, UpdateBounce.Translate.Element (rule_id = rule_id, dsn = dsn, detail = detail, pattern = pattern))
				else:
					ltab = tab[dsn]
					for (index, element) in enumerate (ltab):
						if element.pattern is None:
							ltab[index].detail = detail
							break
					else:
						ltab.append (UpdateBounce.Translate.Element (rule_id = rule_id, dsn = dsn, detail = detail, pattern = pattern))
			else:
				tab[dsn] = [UpdateBounce.Translate.Element (rule_id = rule_id, dsn = dsn, detail = detail, pattern = pattern)]

		def setup (self, db: DB) -> None:
			for row in db.query ('SELECT rule_id, company_id, dsn, detail, pattern FROM bounce_translate_tbl WHERE active = 1'):
				self.add (row.rule_id, row.company_id, row.dsn, row.detail, row.pattern)

		def trans (self, company: int, dsn: int, infos: UpdateBounce.Info) -> Tuple[int, int]:
			def match_pattern (e: UpdateBounce.Translate.Element) -> bool:
				return e.pattern is not None and e.pattern.match (infos)
			def match_default (e: UpdateBounce.Translate.Element) -> bool:
				return e.pattern is None
			for tab in [self.tab[_k] for _k in (company, 0) if _k in self.tab] + [self.default_tab]:
				for predicate in [match_pattern, match_default]:
					nr = dsn
					while nr > 0:
						if nr in tab:
							for element in tab[nr]:
								if predicate (element):
									return (element.rule_id, element.detail)
						nr //= 10
			return (0, Detail.Ignore)
	#}}}
	class DelayedProcessing: #{{{
		__slots__ = ['to_process', 'processing', 'capacity']
		path = os.path.join (base, 'var', 'run', 'delayed-processing.persist')
		def __init__ (self) -> None:
			self.to_process: Dict[Tuple[int, int, int], Tuple[int, str]] = {}
			if os.path.isfile (self.path):
				with open (self.path, 'rb') as fd:
					self.to_process = pickle.load (fd)
					logger.info ('Read {count:,d} delayed bounces from {path}'.format (
						count = len (self.to_process),
						path = self.path
					))
			self.processing = False
			self.capacity = len (self.to_process)

		def __iter__ (self) -> Iterator[Tuple[Tuple[int, int, int], str]]:
			expire = int (time.time ()) - 24 * 60 * 60
			expire_list = (Stream (self.to_process.items ())
				.filter (lambda kv: bool (kv[1][0] < expire))
				.map (lambda kv: (kv[0], kv[1][1]))
				.list ()
			)
			for element in expire_list:
				yield element
		
		def __enter__ (self) -> UpdateBounce.DelayedProcessing:
			if self.processing:
				raise error ('already processing delayed records')
			self.processing = True
			return self
		
		def __exit__ (self, exc_type: Optional[Type[BaseException]], exc_value: Optional[BaseException], traceback: Optional[TracebackType]) -> Optional[bool]:
			self.processing = False
			return None
		
		def __delitem__ (self, key: Tuple[int, int, int]) -> None:
			with Ignore (KeyError):
				del self.to_process[key]
		
		def __len__ (self) -> int:
			return len (self.to_process)

		def done (self) -> None:
			logger.info ('save {count} non processed delayed bounces, had a maximum of {capacity} bounces'.format (
				count = len (self.to_process),
				capacity = self.capacity
			))
			if self.to_process:
				with open (self.path, 'wb') as fd:
					pickle.dump (self.to_process, fd)
					logger.info ('Saved {count:,d} delayed bounces to {path}'.format (
						count = len (self.to_process),
						path = self.path
					))
			elif os.path.isfile (self.path):
				os.unlink (self.path)
				logger.info (f'Removed {self.path} due to no more delayed bounces to process')
		
		def add (self, now: int, line: str, mailing_id: int, media: int, customer_id: int) -> None:
			key = self.make_key (mailing_id, media, customer_id)
			if key not in self.to_process:
				self.to_process[key] = (now, line)
				self.capacity = max (self.capacity, len (self.to_process))
				logger.debug (f'Store for later processing: {line} (now {self.capacity:,d} entries in store)')
			else:
				logger.debug (f'Already stored an entry for {key}: {self.to_process[key]}, skip {line}')
		
		def drop (self, mailing_id: int, media: int, customer_id: int) -> None:
			key = self.make_key (mailing_id, media, customer_id)
			if key in self.to_process:
				logger.debug (f'Remove stored entry for {key}: {self.to_process[key]}')
				del self[key]
		
		def make_key (self, mailing_id: int, media: int, customer_id: int) -> Tuple[int, int, int]:
			return (mailing_id, media, customer_id)
	#}}}
	def __init__ (self) -> None:
		super ().__init__ ()
		self.tracker_age = '3d'
		self.mailing_map: Dict[int, int] = {}
		self.igcount = 0
		self.sucount = 0
		self.sbcount = 0
		self.hbcount = 0
		self.dupcount = 0
		self.blcount = 0
		self.rvcount = 0
		self.ccount = 0
		self.translate = UpdateBounce.Translate ()
		self.delayed_processing = UpdateBounce.DelayedProcessing ()
		self.cache: Cache[Key, Dict[str, Any]] = Cache (limit = 65536)
		self.succeeded: DefaultDict[int, int] = defaultdict (int)
		self.has_mailtrack: Dict[int, bool] = {}
		self.has_mailtrack_last_read = 0
		self.sys_encrypted_sending_enabled: Cache[int, bool] = Cache (timeout = '30m')
		self.bounce_mark_duplicate: Dict[int, bool] = {}
	
	def done (self) -> None:
		self.delayed_processing.done ()
		super ().done ()
	
	dsnparse = re.compile ('^([0-9])\\.([0-9])\\.([0-9]+)$')
	user_unknown = re.compile ('user unknown|unknown user', re.IGNORECASE)
	line_parser = Lineparser (
		lambda a: a.split (';', 5),
		'dsn',
		Field ('licence_id', int),
		Field ('mailing_id', int),
		Field ('media', int),
		Field ('customer_id', int),
		'info'
	)
	@dataclass
	class Breakdown:
		timestamp: datetime
		rule: int
		detail: int
		code: int
		bounce_type: UserStatus
		bounce_remark: Optional[str]
		mailloop_remark: Optional[str]
		infos: Optional[UpdateBounce.Info]

		@staticmethod
		def new (dsn: str, info: str, company_id: int, translate: UpdateBounce.Translate) -> UpdateBounce.Breakdown:
			rc = UpdateBounce.Breakdown (
				timestamp = datetime.now (),
				rule = 0,
				detail = Detail.Ignore,
				code = 0,
				bounce_type = UserStatus.BOUNCE,
				bounce_remark = None,
				mailloop_remark = None,
				infos = None
			)
			match = UpdateBounce.dsnparse.match (dsn)
			if match is not None:
				grp = match.groups ()
				rc.code = int (grp[0]) * 100 + int (grp[1]) * 10 + int (grp[2][0])
				rc.infos = infos = UpdateBounce.Info (info)
				timestamp = Update.timestamp_parser (infos['timestamp'])
				if timestamp is not None:
					rc.timestamp = timestamp
				rc.mailloop_remark = infos['mailloop']
				if rc.mailloop_remark is not None:
					if UpdateBounce.user_unknown.search (rc.mailloop_remark) is not None:
						rc.code = Detail.HardbounceReceiver
				if rc.code % 100 == 99:
					if rc.code // 100 == 5:
						rc.detail = Detail.HardbounceOther
					elif rc.code // 100 == 4:
						rc.detail = Detail.SoftbounceOther
					else:
						rc.detail = Detail.Internal
					admin = infos['admin']
					if admin is not None:
						rc.bounce_type = UserStatus.ADMOUT
						rc.bounce_remark = admin
					status = infos['status']
					if status is not None:
						with Ignore (KeyError):
							rc.bounce_type = UserStatus.find_status (status)
				else:
					rc.rule, rc.detail = translate.trans (company_id, rc.code, infos)
			if rc.bounce_remark is None:
				rc.bounce_remark = f'bounce:{rc.detail} (from {dsn} using {rc.rule})'
			return rc

	def __log_success (self, db: DB, company_id: int, now: int) -> bool:
		if self.has_mailtrack_last_read + 300 < now:
			temp: Dict[int, bool] = {}
			for row in db.query ('SELECT company_id, mailtracking FROM company_tbl'):
				temp[row.company_id] = row.mailtracking == 1
			self.has_mailtrack = temp
			self.has_mailtrack_last_read = now
		for check_company_id in (company_id, 0):
			with Ignore (KeyError):
				return self.has_mailtrack[check_company_id]
		self.has_mailtrack[company_id] = syscfg.bget ('log-success', False)
		return self.has_mailtrack[company_id]

	def __track_store (self, now: int, mailing: int, customer: int, detail: int) -> bool:
		store_it = True
		key = Key (f'{mailing}', f'{customer}')
		record: Dict[str, Any]
		with Ignore (KeyError):
			try:
				record = self.cache[key]
			except KeyError:
				record = self.tracker[key]
			if record['detail'] in Detail.Hardbounces and detail not in Detail.Hardbounces:
				store_it = False
		record = {
			'detail': detail
		}
		self.tracker[key] = record
		self.cache[key] = record
		return store_it

	def update_prepare (self) -> bool:
		self.cache.reset ()
		return True

	def update_start (self, db: DB) -> bool:
		self.igcount = 0
		self.sucount = 0
		self.sbcount = 0
		self.hbcount = 0
		self.dupcount = 0
		self.blcount = 0
		self.rvcount = 0
		self.ccount = 0
		self.bounce_mark_duplicate = (Stream (EMMCompany (db = db, keys = ['bounce-mark-duplicate']).scan_all ())
			.map (lambda v: (v.company_id, atob (v.value)))
			.dict ()
		)
		self.succeeded.clear ()
		self.translate.clear ()
		self.translate.setup (db)
		self.sys_encrypted_sending_enabled.fill = partial (self.column_exists, db, Columns.sys_encrypted_sending)
		self.plugin ().start_bounce (db.cursor, Detail)
		return True

	def column_exists (self, db: DB, column: str, company_id: int) -> bool:
		layout = db.layout (f'customer_{company_id}_tbl', normalize = True)
		return layout is not None and column in {_l.name for _l in layout}
		
	def update_end (self, db: DB) -> bool:
		if self.succeeded:
			logger.info ('Add {mails:,d} mails to {mailings:d} mailings'.format (mails = cast (int, sum (self.succeeded.values ())), mailings = len (self.succeeded)))
			for mailing_id in sorted (self.succeeded):
				db.update ('UPDATE mailing_tbl SET delivered = delivered + :success WHERE mailing_id = :mailing_id', {'success': self.succeeded[mailing_id], 'mailing_id': mailing_id})
			db.sync ()
		logger.info ('Found %d hardbounces (%d duplicates), %d softbounces (%d written), %d successes, %d blacklisted, %d revoked, %d ignored in %d lines' % (self.hbcount, self.dupcount, self.sbcount, (self.sbcount - self.ccount), self.sucount, self.blcount, self.rvcount, self.igcount, self.lineno))
		if self.delayed_processing:
			with self.delayed_processing:
				count = 0
				success = 0
				for (key, line) in self.delayed_processing:
					if self.update_line (db, line):
						success += 1
					del self.delayed_processing[key]
					count += 1
					if count % 1000 == 0:
						db.sync ()
				db.sync ()
				logger.info (f'Apply {count:,d} delayed processed bounces (where {success:,d} succeeded)')
		self.sys_encrypted_sending_enabled.fill = None
		return True

	def update_line (self, db: DB, line: str) -> bool:
		rc = False
		try:
			record = UpdateBounce.line_parser (line)
			if record.licence_id != 0 and record.licence_id != licence:
				logger.debug (f'Ignore bounce for other licenceID {record.licence_id}')
				return True
			if record.customer_id == 0:
				logger.debug ('Ignore virtual recipient')
				return True
			if record.mailing_id <= 0 or record.customer_id < 0:
				logger.warning (f'Got line with invalid mailing or customer: {line}')
				return False
			company_id = self.mailing_to_company (db, record.mailing_id)
			if not company_id:
				logger.warning ('Cannot map mailing %d to company_id for line: %s' % (record.mailing_id, line))
				return False
			breakdown = UpdateBounce.Breakdown.new (record.dsn, record.info, company_id, self.translate)
			if breakdown.detail == Detail.Ignore:
				logger.debug (f'Ignoring line: {line}')
				return True
			if breakdown.detail < 0:
				logger.warning ('Got line with invalid detail (%d): %s' % (breakdown.detail, line))
				return False
			#
			now = int (time.time ())
			if breakdown.detail in Detail.Hardbounces and breakdown.code // 100 != 5:
				if not self.delayed_processing.processing:
					self.delayed_processing.add (now, line, record.mailing_id, record.media, record.customer_id)
					return True
				breakdown.timestamp = datetime.now ()
			#
			with Experimental ('https://jira.agnitas.de/browse/SAAS-2148'):
				if breakdown.detail == Detail.Success or breakdown.detail in Detail.Hardbounces:
					pass
			#
			if breakdown.detail == Detail.Success:
				self.delayed_processing.drop (record.mailing_id, record.media, record.customer_id)
				self.succeeded[record.mailing_id] += 1
				log_to_database = self.__log_success (db, company_id, now)
			else:
				log_to_database = True
			rc = True
			if log_to_database:
				if breakdown.detail == Detail.Success:
					data = {
						'customer': record.customer_id,
						'mailing': record.mailing_id,
						'ts': breakdown.timestamp
					}
					try:
						query = (
							'INSERT INTO success_%d_tbl (customer_id, mailing_id, timestamp) '
							'VALUES (:customer, :mailing, :ts)' % company_id
						)
						db.update (query, data)
						self.plugin ().success (db.cursor, now, company_id, record.mailing_id, record.customer_id, breakdown.timestamp, breakdown.infos)
					except error as e:
						logger.error ('Unable to add success for company_id %d: %s' % (company_id, e))
						rc = False
					#
					self.sucount += 1
					if self.sucount % 1000 == 0:
						db.sync ()
				elif breakdown.bounce_type == UserStatus.BOUNCE:
					data = {
						'company': company_id,
						'customer': record.customer_id,
						'detail': breakdown.detail,
						'mailing': record.mailing_id,
						'dsn': breakdown.code,
						'ts': breakdown.timestamp
					}
					try:
						store = self.__track_store (now, record.mailing_id, record.customer_id, breakdown.detail)
						if store:
							query = db.qselect (
								oracle = (
									'INSERT INTO bounce_tbl '
									'       (bounce_id, company_id, customer_id, detail, mailing_id, dsn, timestamp) '
									'VALUES '
									'       (bounce_tbl_seq.nextval, :company, :customer, :detail, :mailing, :dsn, :ts)'
								), mysql = (
									'INSERT INTO bounce_tbl '
									'       (company_id, customer_id, detail, mailing_id, dsn, timestamp) '
									'VALUES '
									'       (:company, :customer, :detail, :mailing, :dsn, :ts)'
								)
							)
							db.update (query, data)
						elif breakdown.detail not in Detail.Hardbounces:
							self.ccount += 1
						self.plugin ().bounce (db.cursor, store, now, company_id, record.mailing_id, record.customer_id, breakdown.timestamp, breakdown.code, breakdown.detail, breakdown.infos)
					except error as e:
						logger.error ('Unable to add bounce %r to database: %s' % (data, e))
						rc = False
				if breakdown.detail in Detail.Hardbounces or (breakdown.detail == Detail.Internal and breakdown.bounce_type is not None and breakdown.bounce_remark is not None):
					if breakdown.bounce_type == UserStatus.BLACKLIST:
						self.blcount += 1
					else:
						self.hbcount += 1
					try:
						if breakdown.mailloop_remark is not None:
							query = 'DELETE FROM success_%d_tbl WHERE customer_id = :customer_id AND mailing_id = :mailing_id' % company_id
							data = {
								'customer_id': record.customer_id,
								'mailing_id': record.mailing_id
							}
							if db.update (query, data, commit = True) == 1:
								self.rvcount += 1
						if breakdown.detail == Detail.HardbounceNotEncrypted:
							if self.sys_encrypted_sending_enabled[company_id]:
								query = (
									f'UPDATE customer_{company_id}_tbl '
									f'SET {Columns.sys_encrypted_sending} = 0, timestamp = :ts '
									'WHERE customer_id = :customer_id'
								)
								data = {
									'customer_id': record.customer_id,
									'ts': breakdown.timestamp
								}
								db.update (query, data, commit = True)
						else:
							query = (
								'UPDATE customer_%d_binding_tbl '
								'SET user_status = :status, timestamp = :ts, user_remark = :remark, exit_mailing_id = :mailing '
								'WHERE customer_id = :customer_id AND user_status IN (1, 7) AND mediatype = :media'
								% company_id
							)
							data = {
								'status': breakdown.bounce_type.value,
								'remark': breakdown.bounce_remark,
								'mailing': record.mailing_id,
								'ts': breakdown.timestamp,
								'customer_id': record.customer_id,
								'media': record.media
							}
							db.update (query, data, commit = True)
							with Experimental ('EMM-5899'):
								if (
									breakdown.bounce_type == UserStatus.BOUNCE
									and
									record.media == MediaType.EMAIL.value
									and
									self.bounce_mark_duplicate.get (company_id, self.bounce_mark_duplicate.get (0, True))
								):
									rq = db.querys (
										'SELECT email '
										f'FROM customer_{company_id}_tbl '
										'WHERE customer_id = :customer_id',
										{
											'customer_id': record.customer_id
										}
									)
									if rq is not None and rq.email:
										email = rq.email
										try:
											localpart, domain = email.split ('@', 1)
										except ValueError:
											logger.info (f'{record.customer_id}: email "{email}" is not valid for duplicate check')
										else:
											#
											#	find duplicate email addresses, ignoring case
											#	in domain part, but obey case in local part
											customer_ids: List[int] = []
											data['mailing'] = 0
											data['remark'] += f' (by {record.mailing_id})'
											for row in db.queryc (
												'SELECT customer_id, email '
												f'FROM customer_{company_id}_tbl '
												'WHERE lower(email) = lower(:email)',
												{
													'email': email
												}
											):
												if row.customer_id == record.customer_id:
													continue
												check_localpart = row.email.split ('@', 1)[0]
												if check_localpart != localpart:
													continue
												data['customer_id'] = row.customer_id
												if db.update (query, data) > 0:
													logger.info (f'{record.customer_id}: marked as hardbounce due email "{row.email}" seems to be a duplicate of "{email}"')
													customer_ids.append (record.customer_id)
													self.dupcount += 1
											db.sync ()
											#
											with Experimental ('https://jira.agnitas.de/browse/SAAS-2148'):
												for customer_id in customer_ids:
													pass
					except error as e:
						logger.error ('Unable to unsubscribe %r for company %d from database using %s: %s' % (data, company_id, query, e))
						rc = False
				elif breakdown.detail in Detail.Softbounces:
					self.sbcount += 1
					if self.sbcount % 1000 == 0:
						db.sync ()
			else:
				self.igcount += 1
		except Exception as e:
			logger.warning (f'{line}: invalid line: {e}')
		return rc
#}}}
class UpdateAccount (Update): #{{{
	__slots__ = [
		'tscheck', 'ignored', 'inserted', 'bccs', 'failed',
		'status', 'changed',
		'control_parser', 'data_parser', 'insert_query'
	]
	name = 'account'
	path = os.path.join (base, 'log', 'account.log')
	status_template: Final[str] = os.path.join (base, 'scripts', 'mailstatus3.tmpl')
	track_path: Final[str] = os.path.join (base, 'var', 'run', 'update-account.track')
	track_section_mailing: Final[str] = 'mailing'
	track_section_status: Final[str] = 'status'
	@dataclass
	class Mailinfo:
		dirty: bool = True
		active: bool = True
		status_id: int = 0
		mailing_id: int = 0
		company_id: int = 0
		in_production: bool = True
		produced_mails: int = 0
		created_mails: int = 0
		skiped_mails: int = 0
		send_start: int = 0
		send_last: int = 0
		send_count: int = 0
		mail_receiver: str = ''
		mail_sent: bool = False
		mail_percent: int = 50
		def __hash__ (self) -> int:
			return hash ((self.status_id, self.mailing_id))

	def __init__ (self) -> None:
		super ().__init__ ()
		self.tracker_age = '90d'
		self.tscheck = re.compile ('^([0-9]{4})-([0-9]{2})-([0-9]{2}):([0-9]{2}):([0-9]{2}):([0-9]{2})$')
		self.ignored = 0
		self.inserted = 0
		self.bccs = 0
		self.failed = 0
		self.status: Dict[int, UpdateAccount.Mailinfo] = {}
		self.changed: Set[UpdateAccount.Mailinfo] = set ()
		self.control_parser = Tokenparser (
			Field ('licence_id', int, source = 'licence'),
			Field ('owner', int),
			Field ('bcc_count', int, optional = True, default = int, source = 'bcc-count'),
			Field ('bcc_bytes', int, optional = True, default = int, source = 'bcc-bytes')
		)
		self.data_parser = Tokenparser (
			Field ('company_id', int, source = 'company'),
			Field ('mailinglist_id', int, source = 'mailinglist'),
			Field ('mailing_id', int, source = 'mailing'),
			Field ('maildrop_id', int, source = 'maildrop'),
			'status_field',
			Field ('mediatype', int),
			Field ('mailtype', int, source = 'subtype'),
			Field ('no_of_mailings', int, source = 'count'),
			Field ('no_of_bytes', int, source = 'bytes'),
			Field ('skip', int, optional = True, default = int),
			Field ('chunks', int, optional = True, default = lambda: 1),
			Field ('blocknr', int, source = 'block'),
			'mailer',
			Field ('timestamp', ParseTimestamp (), optional = True, default = lambda: datetime.now ())
		)

	def __mailing_status (self, db: DB, status_id: int, count: int, skip: int, ts: int) -> None:
		try:
			minfo = self.status[status_id]
			if not minfo.active:
				logger.debug ('Found inactive mailing %d for new accounting information' % minfo.mailing_id)
			elif not minfo.mailing_id:
				logger.debug ('Found unset mailing_id for new account information with status %d' % status_id)
			else:
				logger.debug ('Found active record with mailing_id %d' % minfo.mailing_id)
			minfo.skiped_mails += skip
			minfo.send_start = max (minfo.send_start, ts)
			minfo.send_last = max (minfo.send_last, ts)
			minfo.send_count += count
			minfo.dirty = True
		except KeyError:
			minfo = UpdateAccount.Mailinfo (
				status_id = status_id,
				skiped_mails = skip,
				send_start = ts,
				send_last = ts,
				send_count = count
			)
			with Ignore (KeyError):
				track = self.tracker[Key (UpdateAccount.track_section_mailing, str (minfo.status_id))]
				minfo.produced_mails = track['produced']
				minfo.skiped_mails += track['skiped']
				minfo.send_count += track['count']
				logger.debug (f'Loaded entry {track!r}')
			db.sync ()
			for r in db.query ('SELECT mailing_id, company_id FROM maildrop_status_tbl WHERE status_id = :status', { 'status': status_id }):
				minfo.mailing_id = r.mailing_id
				minfo.company_id = r.company_id
				break
			if minfo.mailing_id:
				for r in db.queryc ('SELECT statmail_recp, deleted FROM mailing_tbl WHERE mailing_id = :mid', {'mid': minfo.mailing_id}):
					if r[0] is not None:
						receiver = r[0].strip ()
						try:
							(percent, nreceiver) = receiver.split ('%', 1)
							minfo.mail_receiver = nreceiver.strip ()
							minfo.mail_percent = int (percent)
							if minfo.mail_percent < 0 or minfo.mail_percent > 100:
								raise ValueError ('percent value out of range')
						except ValueError:
							minfo.mail_receiver = receiver
					if r[1]:
						logger.info ('Mailing with ID %d is marked as deleted, set to inactive' % minfo.mailing_id)
						minfo.active = False
				db.update ('UPDATE mailing_tbl SET work_status = \'mailing.status.sending\' WHERE mailing_id = :mid AND (work_status IS NULL OR work_status != \'mailing.status.sending\')', { 'mid': minfo.mailing_id }, commit = True)
				logger.debug ('Created new record for status_id %d with mailing_id %d' % (minfo.status_id, minfo.mailing_id))
			else:
				logger.debug ('Created new record for status_id %d with no assigned mailing_id' % status_id)
			self.status[status_id] = minfo
		#
		if minfo.active and minfo.mailing_id:
			self.changed.add (minfo)

	def __mailing_send_status (self, db: DB, minfo: UpdateAccount.Mailinfo) -> None:
		try:
			with open (UpdateAccount.status_template) as fd:
				template = fd.read ()
		except IOError as e:
			logger.warning ('Failed to read template %s: %s' % (self.status_template, e))
		else:
			sender = syscfg.get ('status-sender')
			if minfo.send_last == minfo.send_start:
				last = int (time.time ())
			else:
				last = minfo.send_last
			diff = ((last - minfo.send_start) * minfo.created_mails) / minfo.send_count
			end = minfo.send_start + diff
			start = time.localtime (minfo.send_start)
			then = time.localtime (end)
			rc = db.querys ('SELECT shortname, company_id FROM mailing_tbl WHERE mailing_id = :mid', {'mid': minfo.mailing_id})
			if not rc is None and not None in rc:
				mailing_name = rc.shortname
				company_id = rc.company_id
			else:
				mailing_name = ''
				company_id = 0
			receiver = [_r for _r in minfo.mail_receiver.split () if _r]
			ns = {
				'sender': sender,
				'receiver': ', '.join (receiver),
				'current': minfo.send_count,
				'count': minfo.created_mails,
				'start': datetime (start[0], start[1], start[2], start[3], start[4], start[5]),
				'end': datetime (then[0], then[1], then[2], then[3], then[4], then[5]),
				'mailing_id': minfo.mailing_id,
				'mailing_name': mailing_name,
				'company_id': company_id,

				'format': lambda a: f'{a:,d}'
			}
			tmpl = Template (template)
			try:
				email = tmpl.fill (ns)
			except error as e:
				logger.warning ('Failed to fill template %s: %s' % (UpdateAccount.status_template, e))
			else:
				mail = EMail ()
				if sender:
					mail.set_sender (sender)
				isTo = True
				for r in receiver:
					if isTo:
						mail.add_to (r)
						isTo = False
					else:
						mail.add_cc (r)
				charset = tmpl.property ('charset')
				if charset:
					mail.set_charset (charset)
				subject = tmpl.property ('subject')
				if not subject:
					subject = tmpl['subject']
					if not subject:
						subject = 'Status report for mailing %d [%s]' % (minfo.mailing_id, mailing_name)
				else:
					tmpl = Template (subject)
					subject = tmpl.fill (ns)
				mail.set_subject (subject)
				mail.set_text (email)
				st = mail.send_mail ()
				if not st[0]:
					logger.warning ('Failed to send status mail to %s: [%s/%s]' % (minfo.mail_receiver, st[2].strip (), st[3].strip ()))
				else:
					logger.info ('Status mail for %s (%d) sent to %s' % (mailing_name, minfo.mailing_id, minfo.mail_receiver))

	def __mailing_reached (self, minfo: UpdateAccount.Mailinfo) -> bool:
		if minfo.mail_percent == 100:
			return minfo.created_mails <= minfo.send_count
		return int (float (minfo.created_mails * minfo.mail_percent) / 100.0) <= minfo.send_count

	def __mailing_summary (self, db: DB) -> None:
		for minfo in self.status.values ():
			if minfo.in_production and minfo.active:
				self.changed.add (minfo)
		if self.changed:
			db.sync ()
			for minfo in self.changed:
				if minfo.in_production:
					for r in db.queryc ('SELECT genstatus FROM maildrop_status_tbl WHERE status_id = :status', { 'status': minfo.status_id }):
						if r.genstatus == 3:
							for r in db.query ('SELECT total_mails FROM mailing_backend_log_tbl WHERE status_id = :status', { 'status': minfo.status_id }):
								minfo.in_production = False
								minfo.produced_mails = r.total_mails
								logger.debug ('Changed status for mailing_id %d from production to finished with %d mails produced' % (minfo.mailing_id, minfo.produced_mails))
								break
						break
					if minfo.in_production:
						for r in db.queryc ('SELECT deleted FROM mailing_tbl WHERE mailing_id = :mailing_id', {'mailing_id': minfo.mailing_id}):
							if r.deleted:
								logger.info ('Mailing with ID %d had been deleted, mark as inactive' % minfo.mailing_id)
								minfo.active = False
				if not minfo.in_production:
					minfo.created_mails = minfo.produced_mails - minfo.skiped_mails
					if minfo.mail_receiver and not minfo.mail_sent:
						if self.__mailing_reached (minfo):
							key = Key (UpdateAccount.track_section_status, str (minfo.mailing_id))
							if key not in self.tracker:
								try:
									self.__mailing_send_status (db, minfo)
								except Exception as e:
									logger.exception ('Failed to send status for %s (%d): %s' % (minfo, minfo.mailing_id, e))
								self.tracker[key] = {'sent': int (time.time ()), 'receiver': minfo.mail_receiver}
							minfo.mail_sent = True
					for r in db.query ('SELECT sum(no_of_mailings) FROM mailing_account_tbl WHERE mailing_id = :mid AND status_field = \'W\'', { 'mid': minfo.mailing_id }):
						if r[0] == minfo.created_mails:
							if (count := db.update ('UPDATE mailing_tbl SET work_status = \'mailing.status.sent\' WHERE mailing_id = :mid', { 'mid': minfo.mailing_id }, commit = True)) == 1:
								logger.info ('Changed work status for mailing_id %d to sent' % minfo.mailing_id)
								with Experimental ('https://jira.agnitas.de/browse/SAAS-2148'):
									pass
							elif count == 0:
								logger.warning (f'Failed to change work status for mailing_id {minfo.mailing_id}: the mailing seems to be removed')
							else:
								logger.error ('Failed to change work status for mailing_id %d to sent: %s' % (minfo.mailing_id, db.last_error ()))
							minfo.active = False
							logger.debug ('Changed status for mailing_id %d from active to inactive' % minfo.mailing_id)
						else:
							logger.debug ('Mailing %d has currently %d out of %d sent mails' % (minfo.mailing_id, r[0], minfo.created_mails))
						break
		#
		for minfo in self.status.values ():
			if minfo.dirty:
				self.tracker[Key (UpdateAccount.track_section_mailing, str (minfo.status_id))] = {
					'produced': minfo.produced_mails,
					'skiped': minfo.skiped_mails,
					'count': minfo.send_count
				}
				minfo.dirty = False
				logger.debug (f'Saved entry {minfo}')

	def update_start (self, db: DB) -> bool:
		columns = [_f.name for _f in self.data_parser.fields]
		placeholder = [f':{_c}' for _c in columns]
		if db.dbms == 'oracle':
			columns.insert (0, 'mailing_account_id')
			placeholder.insert (0, 'mailing_account_tbl_seq.nextval')
		self.insert_query = 'INSERT INTO mailing_account_tbl ({columns}) VALUES ({placeholder})'.format (
			columns = ', '.join (columns),
			placeholder = ', '.join (placeholder)
		)
		self.ignored = 0
		self.inserted = 0
		self.bccs = 0
		self.failed = 0
		self.changed.clear ()
		return True

	def update_end (self, db: DB) -> bool:
		logger.info ('Insert %d (%d bccs), failed %d, ignored %d records in %d lines' % (self.inserted, self.bccs, self.failed, self.ignored, self.lineno))
		self.__mailing_summary (db)
		self.changed.clear ()
		return True

	def update_line (self, db: DB, line: str) -> bool:
		try:
			tokens = self.control_parser.parse (line)
			control = self.control_parser (tokens)
			if control.licence_id != licence:
				self.ignored += 1
			else:
				data = self.data_parser (tokens)
				db.update (self.insert_query, data._asdict (), cleanup = True, commit = True)
				self.inserted += 1
				if data.status_field == 'W':
					self.__mailing_status (db, data.maildrop_id, data.no_of_mailings, data.skip, int (data.timestamp.timestamp ()))
				if control.bcc_count > 0:
					db.update (
						db.qselect (
							oracle = (
								'INSERT INTO bcc_mailing_account_tbl '
								'      (mailing_account_id, no_of_mailings, no_of_bytes) '
								'VALUES '
								'      (mailing_account_tbl_seq.currval, :bcc_count, :bcc_bytes)'
							), mysql = (
								'INSERT INTO bcc_mailing_account_tbl '
								'       (mailing_account_id, no_of_mailings, no_of_bytes) '
								'VALUES '
								'       (last_insert_id(), :bcc_count, :bcc_bytes)'
							)
						), {
							'bcc_count': control.bcc_count,
							'bcc_bytes': control.bcc_bytes
						},
						commit = True
					)
					self.bccs += 1
			return True
		except (error, ValueError, KeyError) as e:
			logger.error (f'{self.lineno}: {line!r}: {e}')
			self.failed += 1
		return False
#}}}
class UpdateDeliver (Update): #{{{
	__slots__ = ['existing_deliver_tables', 'count']
	name = 'deliver'
	path = os.path.join (base, 'log', 'deliver.log')
	line_parser = Lineparser (
		lambda a: a.split (';', 4),
		Field ('licence_id', int),
		Field ('mailing_id', int),
		Field ('customer_id', int),
		Field ('timestamp', lambda n: Update.timestamp_parser (n)),
		'line'
	)
	def __init__ (self) -> None:
		super ().__init__ ()
		self.existing_deliver_tables: Set[str] = set ()
		self.count = 0

	def update_start (self, db: DB) -> bool:
		self.count = 0
		return True
		
	def update_end (self, db: DB) -> bool:
		logger.info (f'Added {self.count:,d} out of {self.lineno:,d} new lines')
		return True
	
	def update_line (self, db: DB, line: str) -> bool:
		try:
			record = UpdateDeliver.line_parser (line)
			if record.licence_id != licence:
				logger.debug (f'{record.licence_id}: ignore foreign licence id (own is {licence})')
			else:
				company_id = self.mailing_to_company (db, record.mailing_id)
				if company_id is None:
					logger.info ('mailing {mailing_id}: not found in databsae'.format (mailing_id = record.mailing_id))
				else:
					table = 'deliver_{company_id}_tbl'.format (company_id = company_id)
					if table not in self.existing_deliver_tables:
						if not db.exists (table):
							if db.dbms == 'oracle':
								tablespace = db.find_tablespace ('DATA_SUCCESS')
								db.execute (
									'CREATE TABLE {table} ('
									'id number primary key,'
									'mailing_id number,'
									'customer_id number,'
									'timestamp date,'
									'line varchar2(4000)'
									'){tablespace}'
									.format (
										table = table,
										tablespace = f' TABLESPACE {tablespace}' if tablespace else ''
									)
								)
								db.execute (
									'CREATE SEQUENCE {table}_seq NOCACHE'.format (table = table)
								)
								tablespace = db.find_tablespace ('DATA_CUST_INDEX')
								db.execute (
									'CREATE INDEX del{company_id}$tscid$idx ON {table} (timestamp, customer_id){tablespace}'.format (
										company_id = company_id,
										table = table,
										tablespace = f' TABLESPACE {tablespace}' if tablespace else ''
									)
								)
							else:
								db.execute (
									f'CREATE TABLE {table} ('
									'id int auto_increment primary key,'
									'mailing_id int,'
									'customer_id int,'
									'timestamp datetime,'
									'line varchar(4000)'
									')'
								)
								db.execute (
									f'CREATE INDEX del{company_id}$tscid$idx ON {table} (timestamp, customer_id)'
								)
							db.setup_table_optimizer (table)
						self.existing_deliver_tables.add (table)
					self.count += db.update (db.qselect (
						oracle = (
							'INSERT INTO {table} '
							'       (id, mailing_id, customer_id, timestamp, line) '
							'VALUES '
							'       ({table}_seq.nextval, :mailing_id, :customer_id, :timestamp, :line)'
							.format (table = table)
						), mysql = (
							'INSERT INTO {table} '
							'       (mailing_id, customer_id, timestamp, line) '
							'VALUES '
							'       (:mailing_id, :customer_id, :timestamp, :line)'
							.format (table = table)
						)),
						{
							'mailing_id': record.mailing_id,
							'customer_id': record.customer_id,
							'timestamp': record.timestamp,
							'line': record.line
						}
					)
		except Exception as e:
			logger.warning (f'{line}: invalid line: {e}')
			return False
		else:
			return True
#}}}
class UpdateMailtrack (Update): #{{{
	__slots__ = ['mailtrack_process_table', 'companies', 'count', 'insert_statement', 'max_count', 'max_count_last_updated']
	name = 'mailtrack'
	path = os.path.join (base, 'log', 'mailtrack.log')
	mailtrack_process_table_default = f'mailtrack_process_{unique}_tbl'
	mailtrack_config_key: Final[str] = 'mailtrack-extended'
	mailtrack_bulk_update_config_key: Final[str] = f'{mailtrack_config_key}:bulk-update'
	mailtrack_bulk_update_chunk_config_key: Final[str] = f'{mailtrack_config_key}:bulk-update-chunk'
	line_parser = Lineparser (
		lambda a: a.split (';', 6),
		'id',
		Field ('timestamp', lambda n: datetime.fromtimestamp (int (n))),
		Field ('licence_id', int),
		Field ('company_id', int),
		Field ('mailing_id', int),
		Field ('maildrop_status_id', int),
		Field ('customer_ids', lambda n: [int (_n) for _n in n.split (',') if _n])
	)
	@dataclass
	class CompanyCounter:
		count: int = 0
		mailings: Set[int] = field (default_factory = set)

	def __init__ (self) -> None:
		super ().__init__ ()
		self.mailtrack_process_table = syscfg.get ('mailtrack-process-table', UpdateMailtrack.mailtrack_process_table_default)
		self.companies: DefaultDict[int, UpdateMailtrack.CompanyCounter] = defaultdict (UpdateMailtrack.CompanyCounter)
		self.count = 0
		self.insert_statement = (
			'INSERT INTO {table} '
			'       (company_id, mailing_id, maildrop_status_id, customer_id, timestamp) '
			'VALUES '
			'       (:company_id, :mailing_id, :maildrop_status_id, :customer_id, :timestamp)'
			.format (table = self.mailtrack_process_table)
		)
		self.max_count = 0
		self.max_count_last_updated = 0
		with DBIgnore (), DB () as db:
			if not db.exists (self.mailtrack_process_table):
				tablespace = db.find_tablespace ('DATA_TEMP')
				tablespace_expr = (' TABLESPACE %s' % tablespace) if tablespace else ''
				db.execute (db.qselect (
					oracle = (
						'CREATE TABLE {table} (\n'
						'	company_id		number,\n'
						'	mailing_id		number,\n'
						'	maildrop_status_id	number,\n'
						'	customer_id		number,\n'
						'	timestamp		date\n'
						'){tablespace}'
						.format (table = self.mailtrack_process_table, tablespace = tablespace_expr)
					), mysql = (
						'CREATE TABLE {table} (\n'
						'	company_id		int(11),\n'
						'	mailing_id		int(11),\n'
						'	maildrop_status_id	int(11),\n'
						'	customer_id		integer unsigned,\n'
						'	timestamp		timestamp\n'
						')'
						.format (table = self.mailtrack_process_table)
					)
				))
				mailtrack_index_prefix = syscfg.get ('mailtrack-process-index-prefix', f'mtproc{unique}')
				for (index_id, index_column) in [
					('cid', 'company_id'),
					('cuid', 'customer_id'),
					('tstamp', 'timestamp')
				]:
					db.execute (db.qselect (
						oracle = (
							'CREATE INDEX {prefix}${id}$idx '
							'ON {table} ({column}){tablespace}'
							.format (prefix = mailtrack_index_prefix, id = index_id, column = index_column, table = self.mailtrack_process_table, tablespace = tablespace_expr)
						), mysql = (
							'CREATE INDEX {prefix}${id}$idx '
							'ON {table} ({column})'
							.format (prefix = mailtrack_index_prefix, id = index_id, column = index_column, table = self.mailtrack_process_table)
						)
					))
		
	def update_start (self, db: DB) -> bool:
		db.execute ('TRUNCATE TABLE %s' % self.mailtrack_process_table)
		db.sync ()
		self.companies.clear ()
		self.count = 0
		return True

	def update_end (self, db: DB) -> bool:
		if self.companies:
			#
			# enforce optimizer each new day and when the number of records had increased
			# compared to maximum records of the current day
			today = datetime.now ().toordinal ()
			if self.count > self.max_count or today != self.max_count_last_updated:
				db.setup_table_optimizer (self.mailtrack_process_table, estimate_percent = 10)
				self.max_count = self.count
				self.max_count_last_updated = today
				logger.info (f'optimizer started for {self.count:,d} entries in {self.mailtrack_process_table}')
			#
			emmcompany = EMMCompany (db = db, keys = [
				UpdateMailtrack.mailtrack_config_key,
				UpdateMailtrack.mailtrack_bulk_update_config_key,
				UpdateMailtrack.mailtrack_bulk_update_chunk_config_key
			])
			for (company_id, counter) in sorted (self.companies.items ()):
				try:
					active = emmcompany.get (UpdateMailtrack.mailtrack_config_key, company_id = company_id)
				except KeyError:
					logger.debug ('%s: no value set for company %d, do not process entries' % (UpdateMailtrack.mailtrack_config_key, company_id))
					continue
				else:
					if not atob (active):
						logger.debug ('%s: value %s set for company %d results to disable processing' % (UpdateMailtrack.mailtrack_config_key, active, company_id))
						continue
				#
				bulk_update: bool = emmcompany.get (UpdateMailtrack.mailtrack_bulk_update_config_key, company_id = company_id, default = False, convert = atob)
				bulk_update_chunk: int = emmcompany.get (UpdateMailtrack.mailtrack_bulk_update_chunk_config_key, company_id = company_id, default = 0, convert = atoi)
				logger.info (f'{company_id}: processing {counter} update mailtracking')
				self.__update_mailtracking (db, company_id, counter)
				logger.info (f'{company_id}: processing profile updates (bulk is {bulk_update}, chunk is {bulk_update_chunk})')
				with db.logging (lambda m: logger.info (f'DB: {m}')):
					self.__update_profile (db, company_id, bulk_update, bulk_update_chunk, counter)
				db.sync ()
				logger.info (f'{company_id}: done')
		logger.info ('Added mailtracking for {count} companies'.format (count = len (self.companies)))
		return True	

	def update_line (self, db: DB, line: str) -> bool:
		try:
			record = UpdateMailtrack.line_parser (line)
			if record.licence_id != licence:
				logger.debug (f'{record.licence_id}: ignore foreign licence id (own is {licence})')
			elif record.customer_ids:
				data = {
					'timestamp': record.timestamp,
					'company_id': record.company_id,
					'mailing_id': record.mailing_id,
					'maildrop_status_id': record.maildrop_status_id
				}
				for customer_id in record.customer_ids:
					data['customer_id'] = customer_id
					db.update (self.insert_statement, data)
					self.count += 1
					if self.count % 10000 == 0:
						db.sync ()
						logger.info (f'now at #{self.count:,d}')
				company = self.companies[record.company_id]
				company.count += len (record.customer_ids)
				company.mailings.add (record.mailing_id)
		except Exception as e:
			logger.warning (f'{line}: invalid line: {e}')
			return False
		else:
			return True

	def __update_mailtracking (self, db: DB, company_id: int, counter: UpdateMailtrack.CompanyCounter) -> None:
		rq = db.querys ('SELECT mailtracking FROM company_tbl WHERE company_id = :company_id', {'company_id': company_id})
		if rq is not None and rq.mailtracking:
			mailtrack_table = 'mailtrack_{company_id}_tbl'.format (company_id = company_id)
			if db.exists (mailtrack_table):
				with db.request () as cursor:
					count = cursor.update (
						'INSERT INTO {mailtrack_table} '
						'       (mailing_id, maildrop_status_id, customer_id, timestamp) '
						'SELECT mailing_id, maildrop_status_id, customer_id, timestamp '
						'FROM {table} WHERE company_id = :company_id'
						.format (mailtrack_table = mailtrack_table, table = self.mailtrack_process_table),
						{
							'company_id': company_id
						},
						commit = True,
						sync_and_retry = True
					)
					if count == counter.count:
						logger.debug (f'{company_id}: inserted {count:,d} in {mailtrack_table} as expected')
					else:
						logger.error (f'{company_id}: inserted {count:,d} in {mailtrack_table}, but expected {counter.count:,d}')
			else:
				logger.error ('%d: missing mailtrack table %s' % (company_id, mailtrack_table))
		else:
			logger.debug ('%d: mailtracking is disabled' % company_id)

	def __update_profile (self, db: DB, company_id: int, bulk_update: bool, bulk_update_chunk: int, counter: UpdateMailtrack.CompanyCounter) -> None:
		customer_table = 'customer_{company_id}_tbl'.format (company_id = company_id)
		lastsend_date = 'lastsend_date'
		if not db.exists (customer_table):
			logger.error ('%d: missing customer table %s' % (company_id, customer_table))
			return
		#
		layout = db.layout (customer_table, normalize = True)
		columns = set (_l.name for _l in layout)
		if lastsend_date in columns:
			logger.info (f'{company_id}: update for {lastsend_date} in {customer_table} started')
			count = 0
			if bulk_update:
				query = db.qselect (
					oracle = (
						f'UPDATE {customer_table} cust '
						f'SET cust.{lastsend_date} = '
						f'    (SELECT max(temp.timestamp) FROM {self.mailtrack_process_table} temp WHERE temp.company_id = {company_id} AND temp.customer_id = cust.customer_id) '
						'WHERE EXISTS '
						f'     (SELECT 1 FROM {self.mailtrack_process_table} temp2 WHERE temp2.company_id = {company_id} AND temp2.customer_id = cust.customer_id AND (cust.{lastsend_date} IS NULL OR cust.{lastsend_date} < temp2.timestamp))'
					), mysql = (
						f'UPDATE {customer_table} cust INNER JOIN {self.mailtrack_process_table} temp '
						f'ON (cust.customer_id = temp.customer_id AND temp.company_id = {company_id} AND (cust.{lastsend_date} IS NULL OR cust.{lastsend_date} < temp.timestamp)) '
						f'SET cust.{lastsend_date} = temp.timestamp'
					)
				)
				if bulk_update_chunk > 0:
					count = 0
					query += db.qselect (
						oracle = f' AND rownum <= {bulk_update_chunk}',
						mysql = f' LIMIT {bulk_update_chunk}'
					)
					while True:
						chunk_count = db.update (query, commit = True, sync_and_retry = True)
						if chunk_count > 0:
							count += chunk_count
						else:
							break
				else:
					count = db.update (query, commit = True, sync_and_retry = True)
			else:
				query = (
					'UPDATE {table} '
					'SET {lastsend_date} = :{lastsend_date} '
					'WHERE customer_id = :customer_id AND ({lastsend_date} IS NULL OR {lastsend_date} < :{lastsend_date})'
					.format (table = customer_table, lastsend_date = lastsend_date)
				)
				with db.request () as cursor:
					for (row_count, row) in enumerate (db.queryc (
						'SELECT customer_id, timestamp '
						'FROM {table} '
						'WHERE company_id = :company_id'
						.format (table = self.mailtrack_process_table),
						{
							'company_id': company_id
						}
					), start = 1):
						for state in range (2):
							try:
								count += cursor.update (
									query,
									{
										'customer_id': row.customer_id,
										lastsend_date: row.timestamp
									},
									sync_and_retry = True
								)
							except Exception as e:
								if state == 0:
									cursor.sync ()
									logger.warning (f'Failed to update customer {row.customer_id}, sync and retry: {e}')
									time.sleep (2)
								else:
									logger.error (f'Failed to update customer {row.customer_id}, giving up: {e}')
							else:
								break
						if row_count % 10000 == 0:
							logger.info (f'Now at #{row_count:,d} of #{counter.count:,d}')
							cursor.sync ()
					cursor.sync ()
			logger.info (f'{company_id}: update for {count:,d} {lastsend_date} in {customer_table} while having sent {counter.count:,d}')
		else:
			logger.info (f'{company_id}: no {lastsend_date} in database layout found')
#}}}
class UpdateRelease (Update): #{{{
	__slots__ = ['releases']
	name = 'release'
	path = os.path.join (base, 'log', 'release.log')
	release_log_table: Final[str] = 'release_log_tbl'
	line_parser = Lineparser (
		lambda a: a.split (';', 7),
		Field ('licence_id', int),
		'host',
		'application',
		'version',
		Field ('timestamp', lambda t: Update.timestamp_parser (t)),
		Field ('build_time', lambda t: Update.timestamp_parser (t)),
		'build_host',
		'build_user'
	)
	def __init__ (self) -> None:
		super ().__init__ ()
		self.check_for_duplicates = False
		self.releases: Dict[Tuple[str, str], Line] = {}

	def update_start (self, db: DB) -> bool:
		self.releases.clear ()
		return True
	
	def update_end (self, db: DB) -> bool:
		if self.releases:
			current: Dict[Tuple[str, str], Row] = {}
			for row in db.query (
				'SELECT host_name, application_name, version_number, startup_timestamp '
				f'FROM {UpdateRelease.release_log_table}'
			):
				key = (row.host_name, row.application_name)
				try:
					if current[key].startup_timestamp < row.startup_timestamp:
						current[key] = row
				except KeyError:
					current[key] = row
			for record in self.releases.values ():
				key = (record.host, record.application)
				if key not in current or current[key].version_number != record.version:
					count = db.update (
						f'INSERT INTO {UpdateRelease.release_log_table} '
						'       (host_name, application_name, version_number, startup_timestamp, build_time, build_host, build_user) '
						'VALUES '
						'       (:host_name, :application_name, :version_number, :startup_timestamp, :build_time, :build_host, :build_user)',
						{
							'host_name': record.host,
							'application_name': record.application,
							'version_number': record.version,
							'startup_timestamp': record.timestamp,
							'build_time': record.build_time,
							'build_host': record.build_host,
							'build_user': record.build_user
						},
						commit = True
					)
					if count != 1:
						logger.error (f'{record}: failed to updated one row, updated {count} rows')
					else:
						logger.debug (f'{record}: written to database')
		return True

	def update_line (self, db: DB, line: str) -> bool:
		try:
			record = UpdateRelease.line_parser (line)
			if record.licence_id != licence:
				logger.debug (f'{record.licence_id}: ignore foreign licence id (own is {licence})')
			else:
				key = (record.host, record.application)
				try:
					if self.releases[key].timestamp < record.timestamp:
						self.releases[key] = record
				except KeyError:
					self.releases[key] = record
		except Exception as e:
			logger.warning (f'{line}: invalid line: {e}')
			return False
		else:
			return True
#}}}
#
class Main (Runtime):
	def supports (self, option: str) -> bool:
		return option != 'dryrun'

	def add_arguments (self, parser: argparse.ArgumentParser) -> None:
		parser.add_argument (
			'-S', '--single', action = 'store_true',
			help = 'Execute in a single run without for each module without forking subprocesses'
		)
		parser.add_argument (
			'-D', '--delay', action = 'store', type = int, default = 30,
			help = 'Delay in seconds between scan for new files to process'
		)
		
	def use_arguments (self, args: argparse.Namespace) -> None:
		self.single = args.single
		self.delay = args.delay
		available = dict ((_c.name, _c) for _c in globals ().values () if type (_c) is type and issubclass (_c, Update) and _c is not Update)
		self.modules: List[Type[Update]] = [available[_m] for _m in args.parameter] if args.parameter else list (available.values ())
		if self.single:
			self.ctx.background = False
			self.ctx.watchdog = False

	def prepare (self) -> None:
		if self.single:
			self.ctx.watchdog = False
		else:
			self.ctx.watchdog = True
			
	def start_update (self, update_class: Type[Update]) -> bool:
		with self.title (update_class.name), log (update_class.name):
			upd = update_class ()
			upd.execute (lambda: self.running, self.delay)
		return True

	def executors (self) -> Optional[List[Callable[[], bool]]]:
		if not self.single:
			executors: List[Callable[[], bool]] = []
			for module in self.modules:
				to_execute = partial (self.start_update, module)
				setattr (to_execute, '__name__', module.name)
				executors.append (to_execute)
			return executors
		return None

	def executor (self) -> bool:
		if self.single:
			for module in self.modules:
				upd = module ()
				upd.execute (lambda: True, None)
			return True
		return False
#
if __name__ == '__main__':
	Main.main ()
