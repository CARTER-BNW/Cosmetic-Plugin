// Headless mineflayer client for driving CosmeticPlugin checks on the run-paper dev server.
// Offline-mode bot (no real account). The dev server needs online-mode=false and ViaVersion+ViaBackwards
// so a 1.21.4-protocol client can join Paper 26.2.
//
// Run:     node tools/testbot.js > run/bot.log 2>&1
// Env:     BOT_PORT=25566 BOT_NAME=CosmeticTester BOT_VERSION=1.21.4 MINEFLAYER_DIR=<path to mineflayer module>
// Control: append lines to run/bot-cmds.txt (tools/botdrive.py does this):
//   /command | say text     sent as chat
//   !inv                    dump inventory + SkyCoin count
//   !window                 dump the open window again
//   !click <slot>           left-click a slot in the open window
//   !shiftclick <slot>      shift-click a slot (must NOT move anything in our menus)
//   !close                  close the open window
//   !hotbar <n>             select hotbar slot n
//   !sleep <ms>             pause the command queue
//   !mark <id>              log "MARK <id>" (the driver waits for it)
//   !quit                   disconnect
const fs = require('fs')
const path = require('path')
const mineflayer = require(process.env.MINEFLAYER_DIR || 'D:/EonServer/EON-SETUP/testbot/node_modules/mineflayer')

const CMDS = path.resolve(__dirname, '..', 'run', 'bot-cmds.txt')
fs.writeFileSync(CMDS, '')
let offset = 0

const bot = mineflayer.createBot({
  host: process.env.BOT_HOST || '127.0.0.1',
  port: Number(process.env.BOT_PORT || 25566),
  username: process.env.BOT_NAME || 'CosmeticTester',
  auth: 'offline',
  version: process.env.BOT_VERSION || '1.21.4',
})

const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a)
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function flatten(o) {
  if (o == null) return ''
  if (typeof o === 'string') return o
  if (Array.isArray(o)) return o.map(flatten).join('')
  let s = ''
  if (o.text) s += o.text
  if (o.translate) s += o.translate
  if (o[''] !== undefined) s += flatten(o[''])
  if (o.extra) s += o.extra.map(flatten).join('')
  return s
}
function plain(c) {
  if (c == null) return ''
  if (typeof c === 'string') {
    try { return flatten(JSON.parse(c)) } catch { return c }
  }
  return flatten(c)
}
function itemText(it) {
  const name = it.customName ? plain(it.customName) : (it.displayName || it.name)
  let lore = ''
  try { if (it.customLore) lore = it.customLore.map(plain).filter(Boolean).join(' | ') } catch { /* ignore */ }
  return `${it.name} x${it.count} "${name}"${lore ? ` lore=[${lore}]` : ''}`
}
function dumpWindow(w) {
  const title = w.title ? plain(w.title) : '?'
  const end = typeof w.inventoryStart === 'number' ? w.inventoryStart : w.slots.length
  log(`WINDOW "${title}" size=${end}`)
  for (let i = 0; i < end; i++) {
    const it = w.slots[i]
    if (it && !it.name.endsWith('stained_glass_pane')) log(`  slot ${i}: ${itemText(it)}`)
  }
}
function dumpInv() {
  const items = bot.inventory.items()
  let coins = 0
  if (!items.length) log('INV: (empty)')
  for (const it of items) {
    log(`INV slot ${it.slot}: ${itemText(it)}`)
    if (it.customName && plain(it.customName).includes('SkyCoin')) coins += it.count
  }
  log(`COINS=${coins} (items named SkyCoin)`)
}

bot.on('login', () => log('LOGIN OK as', bot.username))
bot.on('spawn', () => log('SPAWNED'))
bot.on('messagestr', (m) => { if (m.trim()) log('CHAT:', m.replace(/\s+/g, ' ').trim()) })
bot.on('kicked', (r) => log('KICKED:', JSON.stringify(r)))
bot.on('error', (e) => log('ERROR:', e.message))
bot.on('end', (r) => { log('DISCONNECTED:', r); process.exit(0) })
bot.on('windowOpen', (w) => dumpWindow(w))
bot.on('windowClose', () => log('WINDOW CLOSED'))

async function exec(line) {
  line = line.trim()
  if (!line) return
  log('EXEC:', line)
  try {
    if (line === '!inv') return dumpInv()
    if (line === '!window') return bot.currentWindow ? dumpWindow(bot.currentWindow) : log('WINDOW: none open')
    if (line.startsWith('!slot ')) {   // raw dump of one window slot: name/lore in whatever form the protocol delivered
      const slot = Number(line.split(' ')[1])
      const it = bot.currentWindow ? bot.currentWindow.slots[slot] : null
      if (!it) return log(`SLOT ${slot}: empty or no window`)
      const raw = { name: it.name, count: it.count, customName: it.customName, customLore: it.customLore, nbt: it.nbt, components: it.components }
      return log(`SLOT ${slot}: ${JSON.stringify(raw).slice(0, 1500)}`)
    }
    if (line === '!close') { if (bot.currentWindow) bot.closeWindow(bot.currentWindow); return }
    if (line === '!quit') { bot.quit(); return }
    if (line.startsWith('!sleep ')) return sleep(Number(line.split(' ')[1]))
    if (line.startsWith('!mark ')) return log('MARK', line.split(' ')[1])
    if (line.startsWith('!hotbar ')) { bot.setQuickBarSlot(Number(line.split(' ')[1])); return }
    if (line.startsWith('!click ') || line.startsWith('!shiftclick ')) {
      const slot = Number(line.split(' ')[1])
      const mode = line.startsWith('!shift') ? 1 : 0
      if (!bot.currentWindow) return log('CLICK ERR: no window open')
      await bot.clickWindow(slot, 0, mode).catch((e) => log('CLICK ERR:', e.message))
      await sleep(400)
      return
    }
    bot.chat(line)
  } catch (e) { log('EXEC ERR:', e.message) }
}

const queue = []
let busy = false
async function pump() {
  if (busy) return
  busy = true
  while (queue.length) await exec(queue.shift())
  busy = false
}
setInterval(() => {
  try {
    const size = fs.statSync(CMDS).size
    if (size > offset) {
      const fd = fs.openSync(CMDS, 'r')
      const buf = Buffer.alloc(size - offset)
      fs.readSync(fd, buf, 0, buf.length, offset)
      fs.closeSync(fd)
      offset = size
      queue.push(...buf.toString('utf8').split(/\r?\n/))
      pump()
    }
  } catch { /* file busy, retry */ }
}, 300)
