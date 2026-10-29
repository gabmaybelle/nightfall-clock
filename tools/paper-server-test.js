#!/usr/bin/env node
// Starts a Paper server with the plugin and checks what the clock reads, through /clock selftest
// (an operator command that prints the clock line in both formats, the countdown, the weather and
// when dusk and thunder last arrived): the time at noon, 7:00 AM, midnight and 6:00 PM, the
// countdown to dark at 6:00 PM, 6:42 PM and just before dark, no countdown once it's dark, the day
// number, dusk arriving as the day runs (and not when the time jumps past it), thunder arriving
// (and no countdown while it thunders), the Overworld's time in the Nether, and that /clock from
// the console says it's for players. No window opens; the server is stopped at the end. The Paper
// server jar is downloaded once into the folder.
//
//   node tools/paper-server-test.js "<server folder>" --plugin paper/build/libs/nightfallclock-paper-<v>.jar --eula
//       [--paper 26.3] [--port <n>] [--no-countdown] [--timeout <seconds>]
//
// --eula says you accept Minecraft's server EULA (https://aka.ms/MinecraftEULA); without it the
// script stops before starting the server. --no-countdown sets the plugin's countdown off, so the
// countdown checks are expected to fail (the proof that they test something).
'use strict';
const fs = require('fs'), path = require('path'), cp = require('child_process'), crypto = require('crypto');

const args = process.argv.slice(2);
const folder = args[0] && !args[0].startsWith('--') ? path.resolve(args[0]) : null;
const opt = (name, fallback) => { const i = args.indexOf(name); return i >= 0 && i + 1 < args.length ? args[i + 1] : fallback; };
const flag = (name) => args.includes(name);
const plugin = opt('--plugin', null);
if (!folder || !plugin) {
	console.error('usage: node tools/paper-server-test.js "<server folder>" --plugin <jar> --eula [--paper 26.3] [--port <n>] [--no-countdown] [--timeout <s>]');
	process.exit(2);
}
if (!flag('--eula')) {
	console.error('The server only runs once the Minecraft server EULA (https://aka.ms/MinecraftEULA) is accepted: pass --eula to say you do.');
	process.exit(2);
}
const paperVersion = opt('--paper', '26.3');
const port = +opt('--port', '25598');
const timeoutMs = +opt('--timeout', '600') * 1000;
const javaBin = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const USER_AGENT = 'nightfall-clock-server-test (https://github.com/gabmaybelle/nightfall-clock)';

async function download(url, to, sha256) {
	const res = await fetch(url, { headers: { 'User-Agent': USER_AGENT } });
	if (!res.ok) throw new Error(`${url}: ${res.status}`);
	const data = Buffer.from(await res.arrayBuffer());
	if (sha256 && crypto.createHash('sha256').update(data).digest('hex') !== sha256) throw new Error(`${url}: checksum mismatch`);
	fs.writeFileSync(to, data);
}

/** The newest Paper build of this version, downloaded once as paper-<version>-<build>.jar. */
async function serverJar() {
	const existing = fs.readdirSync(folder).filter((f) => f.startsWith(`paper-${paperVersion}-`) && f.endsWith('.jar')).sort();
	if (existing.length) return path.join(folder, existing[existing.length - 1]);
	const res = await fetch(`https://fill.papermc.io/v3/projects/paper/versions/${paperVersion}/builds/latest`, { headers: { 'User-Agent': USER_AGENT } });
	if (!res.ok) throw new Error(`Paper build list: ${res.status}`);
	const build = await res.json();
	const dl = build.downloads['server:default'];
	console.log(`downloading Paper ${paperVersion} build ${build.id} (${dl.name})`);
	const to = path.join(folder, dl.name);
	await download(dl.url, to, dl.checksums && dl.checksums.sha256);
	return to;
}

function prepare() {
	fs.mkdirSync(path.join(folder, 'plugins', 'NightfallClock'), { recursive: true });
	fs.writeFileSync(path.join(folder, 'eula.txt'), 'eula=true\n');
	// A flat world: the test only reads the clock, so there's nothing to generate.
	fs.writeFileSync(path.join(folder, 'server.properties'), [
		'online-mode=false', `server-port=${port}`, 'query.port=' + port, 'enable-rcon=false', 'white-list=false',
		'spawn-protection=0', 'view-distance=4', 'simulation-distance=4', 'max-players=2', 'motd=Nightfall Clock test',
		'level-type=minecraft\\:flat', 'level-name=world', 'sync-chunk-writes=false', ''].join('\n'));
	// Only this plugin, and only the jar under test.
	for (const f of fs.readdirSync(path.join(folder, 'plugins'))) {
		if (f.endsWith('.jar')) fs.rmSync(path.join(folder, 'plugins', f));
	}
	fs.copyFileSync(plugin, path.join(folder, 'plugins', path.basename(plugin)));
	// The plugin's settings for this run: its defaults, with the countdown off for the proof run.
	const config = path.join(folder, 'plugins', 'NightfallClock', 'config.yml');
	if (flag('--no-countdown')) {
		fs.writeFileSync(config, 'countdown: false\n');
	} else {
		fs.rmSync(config, { force: true });
	}
	// A fresh world each run, so the day count starts at 1.
	for (const w of ['world', 'world_nether', 'world_the_end']) fs.rmSync(path.join(folder, w), { recursive: true, force: true });
}

/** Runs the server, feeding it console commands and reading their replies. */
class Server {
	constructor(jar) {
		this.lines = [];
		this.waiters = [];
		// The clock line has a middle dot in it, which a Windows console would print as "?" without UTF-8 output.
		this.proc = cp.spawn(javaBin, ['-Xmx2G', '-Dcom.mojang.eula.agree=true', '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-jar', jar, 'nogui'],
			{ cwd: folder, stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true });
		let buffer = '';
		const onData = (chunk) => {
			buffer += chunk.toString();
			let i;
			while ((i = buffer.indexOf('\n')) >= 0) {
				const line = buffer.slice(0, i).replace(/\r$/, '');
				buffer = buffer.slice(i + 1);
				this.lines.push(line);
				for (const w of [...this.waiters]) if (w.test(line)) { this.waiters.splice(this.waiters.indexOf(w), 1); w.resolve(line); }
			}
		};
		this.proc.stdout.on('data', onData);
		this.proc.stderr.on('data', onData);
		this.exited = new Promise((resolve) => this.proc.on('exit', (code) => { this.exitCode = code; resolve(code); }));
	}

	/** The next line matching the pattern (or one already seen since `from`), within the time given. */
	waitFor(pattern, ms, from = 0) {
		const found = this.lines.slice(from).find((l) => pattern.test(l));
		if (found) return Promise.resolve(found);
		return new Promise((resolve, reject) => {
			const w = { test: (l) => pattern.test(l), resolve: (l) => { clearTimeout(timer); resolve(l); } };
			const timer = setTimeout(() => { this.waiters.splice(this.waiters.indexOf(w), 1); reject(new Error(`timed out waiting for ${pattern}`)); }, ms);
			this.waiters.push(w);
		});
	}

	/**
	 * Sends a console command and returns what the server said to it, without the log prefix and
	 * colour codes. The console prints a vanilla command's reply as "System chat: ..."; the plugin's
	 * own replies are plain lines, so a pattern for them can be given.
	 */
	async command(text, reply = /System chat: |Unknown or incomplete command|Incorrect argument/) {
		const from = this.lines.length;
		this.proc.stdin.write(text + '\n');
		const line = await this.waitFor(reply, 15000, from);
		return line.replace(/^.*?\]: /, '').replace(/\x1b\[[0-9;]*m/g, '');
	}

	async stop() {
		if (this.exitCode !== undefined) return;
		try { this.proc.stdin.write('stop\n'); } catch (e) { /* already gone */ }
		const t = setTimeout(() => this.proc.kill(), 60000);
		await this.exited;
		clearTimeout(t);
	}
}

/** What /clock selftest printed, as fields: time, 12h and 24h lines, gold, seconds, thundering, dusk, storm. */
function parse(reply) {
	const field = (name, pattern) => { const m = new RegExp(`· ${name} ${pattern}`).exec(reply); return m ? m[1] : null; };
	return {
		time: +field('time', '(-?\\d+)'),
		twelve: field('12h', '"([^"]*)"'),
		twentyFour: field('24h', '"([^"]*)"'),
		gold: field('gold', '(true|false)') === 'true',
		seconds: +field('seconds', '(-?\\d+)'),
		thundering: field('thundering', '(true|false)') === 'true',
		dusk: field('dusk', '(none|at \\d+)'),
		storm: field('storm', '(none|at \\d+)'),
	};
}

async function main() {
	fs.mkdirSync(folder, { recursive: true });
	const jar = await serverJar();
	prepare();
	console.log(`starting Paper (${path.basename(jar)}) in ${folder} with ${path.basename(plugin)}`);
	const server = new Server(jar);
	const failures = [];
	const check = (name, ok, detail) => { console.log(`${ok ? 'ok  ' : 'FAIL'} ${name}${detail ? ': ' + detail : ''}`); if (!ok) failures.push(name); };
	const SELFTEST = /Nightfall Clock selftest:|Unknown or incomplete command|System chat: /;
	/** Sets the time of day, then reads the clock the next tick (so the time read is within a few ticks of the one set). */
	const readAt = async (time) => {
		await server.command(`time set ${time}`);
		const reply = await server.command('clock selftest', SELFTEST);
		return { reply, ...parse(reply) };
	};
	/** The clock's reading at this time: the 12-hour line, the 24-hour line, gold or not. The time read may be a few ticks on. */
	const reading = async (name, time, twelve, twentyFour, gold) => {
		const r = await readAt(time);
		// The time read is the time of day (0-23999); the time set may count whole days as well.
		const soon = r.time >= time % 24000 && r.time <= time % 24000 + 15;
		check(`${name}: time read within 15 ticks of ${time % 24000}`, soon, `read ${r.time}`);
		check(`${name}: 12-hour "${twelve}"`, r.twelve === twelve, r.twelve);
		check(`${name}: 24-hour "${twentyFour}"`, r.twentyFour === twentyFour, r.twentyFour);
		check(`${name}: ${gold ? 'gold' : 'white'}`, r.gold === gold, `gold ${r.gold}`);
		return r;
	};
	try {
		await Promise.race([server.waitFor(/Done \(.*\)! For help/, timeoutMs), server.exited.then(() => { throw new Error('the server stopped before it was ready'); })]);
		const enabled = server.lines.find((l) => /Enabling NightfallClock/.test(l));
		check('the plugin was enabled', !!enabled, enabled ? enabled.replace(/^.*?\]: /, '') : 'no "Enabling NightfallClock" line');
		await server.command('weather clear 100000');

		// The clock at a few times of day: tick 0 is 6:00 AM, noon is 6000, 6:00 PM is 12000.
		await reading('noon', 6000, 'Day 1 · 12:00 PM', 'Day 1 · 12:00', false);
		await reading('7:00 AM', 1000, 'Day 1 · 7:00 AM', 'Day 1 · 07:00', false);
		await reading('midnight', 18000, 'Day 1 · 12:00 AM', 'Day 1 · 00:00', false);
		// From 6:00 PM the clock is gold and counts down to the moment monsters can spawn outside
		// (vanilla's sky light reaches 7 at tick 13178 or so): 1178 ticks at 20 a second is 59 s.
		const sunset = await reading('6:00 PM', 12000, 'Day 1 · 6:00 PM · dark in 59s', 'Day 1 · 18:00 · dark in 59s', true);
		check('6:00 PM: 59 seconds to dark', sunset.seconds === 59, `seconds ${sunset.seconds}`);
		await reading('6:42 PM', 12700, 'Day 1 · 6:42 PM · dark in 24s', 'Day 1 · 18:42 · dark in 24s', true);
		await reading('7:06 PM, still gold while counting down', 13100, 'Day 1 · 7:06 PM · dark in 4s', 'Day 1 · 19:06 · dark in 4s', true);
		const dark = await reading('7:18 PM, dark', 13300, 'Day 1 · 7:18 PM', 'Day 1 · 19:18', false);
		check('dark: no countdown', dark.seconds === -1, `seconds ${dark.seconds}`);
		await reading('day 4', 3 * 24000 + 12700, 'Day 4 · 6:42 PM · dark in 24s', 'Day 4 · 18:42 · dark in 24s', true);

		// Dusk arrives when the day runs through 6:00 PM, not when the time jumps past it.
		const before = await readAt(11900);
		check('before dusk: no dusk seen yet', before.dusk === 'none', before.dusk);
		await sleep(8000);
		const arrived = { reply: await server.command('clock selftest', SELFTEST) };
		Object.assign(arrived, parse(arrived.reply));
		check('dusk arrived as the day ran through 6:00 PM', /^at \d+$/.test(arrived.dusk) && arrived.time >= 12000, `dusk ${arrived.dusk}, time ${arrived.time}`);
		await server.command('time set 11000');
		const jumped = await readAt(13000);
		check('a jump past 6:00 PM is not dusk arriving', jumped.dusk === arrived.dusk, `dusk ${jumped.dusk} (was ${arrived.dusk})`);

		// Thunder: a storm arriving after clear weather, and no countdown while it thunders.
		await sleep(2000);
		const calm = await readAt(12500);
		check('clear weather: no storm seen yet', calm.storm === 'none' && !calm.thundering, `storm ${calm.storm}, thundering ${calm.thundering}`);
		check('clear weather at 6:30 PM: counting down', calm.seconds > 0 && /dark in/.test(calm.twelve), calm.twelve);
		await server.command('weather thunder 100000');
		await sleep(1500);
		const thunder = { reply: await server.command('clock selftest', SELFTEST) };
		Object.assign(thunder, parse(thunder.reply));
		check('thunder arrived', /^at \d+$/.test(thunder.storm) && thunder.thundering, `storm ${thunder.storm}, thundering ${thunder.thundering}`);
		check('thunder: no countdown, monsters can spawn already', thunder.seconds === -1 && !/dark in/.test(thunder.twelve), thunder.twelve);
		check('thunder at 6:30 PM: still gold', thunder.gold, `gold ${thunder.gold}`);
		await server.command('weather clear 100000');
		await sleep(1500);
		const cleared = { reply: await server.command('clock selftest', SELFTEST) };
		Object.assign(cleared, parse(cleared.reply));
		check('clear again: the countdown is back', cleared.seconds > 0 && !cleared.thundering, `seconds ${cleared.seconds}, thundering ${cleared.thundering}`);

		// The Nether shows the Overworld's day and time, with no countdown.
		await server.command('time set 12700');
		const nether = await server.command('execute in minecraft:the_nether run clock selftest', SELFTEST);
		const n = parse(nether);
		check('the Nether shows the Overworld\'s time', /^Overworld: Day 1 · 6:4[23] PM$/.test(n.twelve) && /world world_nether/.test(nether), n.twelve);
		check('the Nether: no countdown', n.seconds === -1, `seconds ${n.seconds}`);

		// /clock on its own is for a player.
		const console_ = await server.command('clock', /Nightfall Clock|Only a player|Unknown or incomplete command|System chat: /);
		check('/clock from the console says it is for players', /Only a player has a clock/.test(console_), console_);

		const errors = server.lines.filter((l) => /^\[[\d:]+ (ERROR|WARN)\]/.test(l) && /NightfallClock|Nightfall Clock|nightfallclock/i.test(l));
		check('no errors or warnings from the plugin', errors.length === 0, errors.join(' | '));
	} catch (e) {
		check('the test ran to the end', false, e.message);
	} finally {
		await server.stop();
		fs.writeFileSync(path.join(folder, 'test-console.log'), server.lines.join('\n'));
		console.log(failures.length ? `FAILED: ${failures.join(', ')}` : 'passed: every check');
		process.exit(failures.length ? 1 : 0);
	}
}

main().catch((e) => { console.error(e); process.exit(1); });
