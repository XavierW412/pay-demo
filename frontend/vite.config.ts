import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { chmodSync, copyFileSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join, relative } from 'node:path'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import type { Plugin } from 'vite'

const root = fileURLToPath(new URL('.', import.meta.url))
const appVersion = (JSON.parse(readFileSync(join(root, 'package.json'), 'utf8')) as { version: string }).version

const privateSourceMaps = (): Plugin => ({
  name: 'private-source-maps',
  apply: 'build',
  writeBundle(options, bundle) {
    const output = options.dir ?? join(root, 'dist')
    const maps = new Set<string>()
    const failures: unknown[] = []
    // 即使目录扫描失败，也能从生成清单取得应清除的映射路径。
    for (const [fileName, entry] of Object.entries(bundle)) {
      if (fileName.endsWith('.map')) maps.add(join(output, fileName))
      if (entry.type === 'chunk' && entry.map) maps.add(join(output, `${fileName}.map`))
    }
    const scan = (directory: string, found: Set<string>) => {
      try {
        const children = readdirSync(directory, { withFileTypes: true })
        for (const entry of children) {
          const path = join(directory, entry.name)
          if (entry.isDirectory()) scan(path, found)
          else if (entry.name.endsWith('.map')) found.add(path)
        }
      } catch (error) {
        failures.push(error)
      }
      // 每个子目录自己记录失败，其他可读目录继续扫描。
    }
    const removeMaps = (paths: Set<string>) => {
      for (const path of paths) {
        try { rmSync(path, { force: true }) }
        catch (error) { failures.push(error) }
      }
    }
    const privateRoot = join(root, '.cache', 'private-source-maps')
    const versionRoot = join(privateRoot, appVersion)
    const destination = join(versionRoot, `${Date.now()}`)
    try {
      scan(output, maps)
      for (const directory of [privateRoot, versionRoot, destination]) {
        mkdirSync(directory, { recursive: true, mode: 0o700 })
        chmodSync(directory, 0o700)
      }
      for (const source of maps) {
        const target = join(destination, relative(output, source))
        mkdirSync(dirname(target), { recursive: true, mode: 0o700 })
        chmodSync(dirname(target), 0o700)
        copyFileSync(source, target)
        chmodSync(target, 0o600)
      }
      writeFileSync(join(destination, 'manifest.json'), JSON.stringify({ appVersion, files: Array.from(maps, (path) => relative(output, path)) }), { mode: 0o600 })
    } catch (error) {
      failures.push(error)
    } finally {
      removeMaps(maps)
      // 多轮扫描和删除，确保首次扫描异常或单个删除失败时仍继续清理其他文件。
      for (let attempt = 0; attempt < 2; attempt += 1) {
        const discovered = new Set<string>()
        scan(output, discovered)
        removeMaps(discovered)
      }
      // 对已发现的残留再做一次尽力清理，随后最终扫描确认公开目录状态。
      const remaining = new Set<string>()
      scan(output, remaining)
      removeMaps(remaining)
      const finalRemaining = new Set<string>()
      scan(output, finalRemaining)
      if (finalRemaining.size) failures.push(new Error('公开构建目录仍包含映射文件'))
    }
    if (failures.length) throw new AggregateError(failures, '私有映射保存或公开映射清理失败，禁止发布此次产物')
  },
})

export default defineConfig({
  plugins: [react(), tailwindcss(), privateSourceMaps()],
  define: { 'import.meta.env.VITE_APP_VERSION': JSON.stringify(appVersion) },
  build: { sourcemap: 'hidden' },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
