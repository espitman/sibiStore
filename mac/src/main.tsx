import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { ShoppingBag, LayoutGrid, Download, Settings, Folder, RefreshCw, Search, ChevronRight, ChevronDown, Copy, Monitor, House, Play, FileBox, AlertCircle, Check, Wifi, ArrowDownToLine, CircleHelp, X } from 'lucide-react';
import type { App, State } from './types';
import './style.css';

const size = (n: number) => n >= 1048576 ? `${Math.round(n / 1048576)} MB` : `${Math.round(n / 1024)} KB`;
const date = (v: string) => new Date(v).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false });
function AppIcon({ app, large = false }: { app: App; large?: boolean }) {
  if (app.icon) return <img className={`app-icon ${large ? 'large' : ''}`} src={app.icon} alt="" />;
  const name = app.title.toLowerCase();
  return <span className={`app-icon fallback ${large ? 'large' : ''} ${name.includes('sibi') ? 'sibi-icon' : ''}`}>
    {name.includes('sibi') ? <Play fill="currentColor" strokeWidth={0} size={large ? 54 : 29}/> : <span>{app.title.slice(0, 1).toUpperCase()}</span>}
  </span>;
}
function Desktop() {
  const [state, setState] = useState<State | null>(null);
  const [page, setPage] = useState('Library'); const [query, setQuery] = useState('');
  const [platform, setPlatform] = useState<'All' | 'Phone' | 'TV'>('All');
  const [selected, setSelected] = useState('com.sibi.player'); const [details, setDetails] = useState(false);
  const [error, setError] = useState(''); const [copied, setCopied] = useState(false);
  const invoke = async (fn: () => Promise<unknown>) => { try { await fn(); } catch(e) { setError(e instanceof Error ? e.message : String(e)); } };
  useEffect(() => { window.sibi.snapshot().then(setState).catch(e => setError(e.message)); return window.sibi.onChange(setState); }, []);
  const platformApps = (state?.apps || []).flatMap(a => {
    const versions = a.versions.filter(v => platform === 'All' || v.tv === (platform === 'TV'));
    return versions.length ? [{ ...a, versions }] : [];
  });
  const apps = platformApps.filter(a => `${a.title} ${a.packageName}`.toLowerCase().includes(query.toLowerCase()));
  const app = apps.find(a => a.packageName === selected) || apps[0];
  const version = app?.versions[0];
  const active = state?.transfers.filter(t => t.status === 'active') || [];
  const versions = state?.apps.reduce((n,a) => n + a.versions.length, 0) || 0;
  return <div className="desktop">
    <aside className="sidebar">
      <div className="traffic-space"/>
      <div className="brand">sibi<span>store</span><svg width="32" height="32" viewBox="0 0 24 28" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><rect x="3" y="8" width="18" height="18" rx="2"/><path d="M8 11V5a4 4 0 0 1 8 0v6M8 17l3 3 5-5"/></svg></div>
      <div className="server-label">SERVER{state?.preview ? ' · DESIGN PREVIEW' : ''}</div>
      <nav>{[{ title: 'Library', Icon: LayoutGrid }, { title: 'Transfers', Icon: Download }, { title: 'Settings', Icon: Settings }].map(({title,Icon}) => <button key={title} className={`nav-item ${page === title ? 'active' : ''}`} onClick={() => setPage(title)}><Icon size={23}/>{title}{title === 'Transfers' && active.length > 0 && <span className="badge">{active.length}</span>}</button>)}</nav>
      <div className="sidebar-status"><div><i className={`dot ${state?.running ? '' : 'off'}`}/>{state?.running ? 'Server running' : 'Server stopped'}</div><div className="muted"><House size={15}/>Home network</div></div>
    </aside>
    <section className="workspace">
      <header className="toolbar"><strong>{page}</strong><div className="toolbar-actions">{page === 'Library' && <label className="search"><Search size={19}/><input aria-label="Search apps" placeholder="Search apps" value={query} onChange={e => setQuery(e.target.value)}/>{query && <button className="icon-button" aria-label="Clear search" onClick={() => setQuery('')}><X size={15}/></button>}</label>}<button className="text-button" disabled={state?.scanning} onClick={() => invoke(() => window.sibi.rescan())}><RefreshCw size={19} className={state?.scanning ? 'spin' : ''}/>{state?.scanning ? 'Scanning…' : 'Rescan'}</button><button className="gold-button" onClick={() => invoke(() => window.sibi.openFolder())}><Folder size={21}/>Open folder</button></div></header>
      {(error || state?.serverError) && <div className="error-banner"><AlertCircle size={18}/>{error || state?.serverError}<button aria-label="Dismiss" onClick={() => setError('')}><X size={16}/></button></div>}
      {page === 'Library' && <main className="library-page">
        <div className="page-heading"><h1>App library</h1><p>APKs from your shared folder</p></div>
        <div className="library-columns">
          <div className="library-main">
            <div className="folder-strip"><Folder size={20}/><span className="folder-path" title={state?.folder}>{state?.folder || 'Loading library…'}</span><span className="strip-divider"/><span>{state?.apps.length || 0} apps</span><span className="strip-divider"/><span>{versions} versions</span><small>{state?.scanning ? 'Scanning…' : state?.lastScan ? 'Scanned just now' : 'Not scanned'}</small></div>
            {state?.errors.length ? <details className="scan-errors"><summary><AlertCircle size={16}/>{state.errors.length} {state.errors.length === 1 ? 'file needs' : 'files need'} attention</summary>{state.errors.map((e,i) => <p key={i}><strong>{e.file}</strong><br/>{e.message}</p>)}</details> : null}
            <div className="platform-filters" role="group" aria-label="Filter by platform">{(['All', 'Phone', 'TV'] as const).map(value => <button key={value} aria-pressed={platform === value} className={platform === value ? 'active' : ''} onClick={() => { setPlatform(value); setDetails(false); }}>{value}<span>{(state?.apps || []).filter(a => value === 'All' || a.versions.some(v => v.tv === (value === 'TV'))).length}</span></button>)}</div>
            <div className="table-scroll"><table><thead><tr><th>App</th><th>Latest version</th><th>Size</th><th>Status</th></tr></thead><tbody>{apps.map(a => <tr key={a.packageName} className={a.packageName === app?.packageName ? 'selected' : ''} tabIndex={0} onClick={() => { setSelected(a.packageName); setDetails(false); }} onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setSelected(a.packageName); } }} aria-selected={a.packageName === app?.packageName}><td><div className="app-cell"><AppIcon app={a}/><div><strong>{a.title}</strong><small title={a.packageName}>{a.packageName}</small></div></div></td><td>{a.versions[0].versionName || a.versions[0].versionCode}</td><td>{size(a.versions[0].size)}</td><td><span className="ready"><i className="dot"/>Ready</span></td></tr>)}</tbody></table>
              {!apps.length && <div className="empty"><FileBox size={42}/><h2>{query ? 'No matching apps' : platform !== 'All' ? `No ${platform === 'TV' ? 'TV' : 'phone'} apps` : 'Your library starts here'}</h2><p>{query ? 'Try another app name or package.' : platform !== 'All' ? 'Choose All or copy matching APKs into your folder.' : 'Copy APKs into your folder. Sibi Store will take care of the rest.'}</p>{!query && <button className="outline-button" onClick={() => invoke(() => window.sibi.openFolder())}><Folder size={18}/>Open APK folder</button>}</div>}
            </div>
            <div className="table-footer">{apps.length} of {state?.apps.length || 0} apps <span><Wifi size={14}/>{state?.running ? `Listening on port ${state.port}` : 'Server offline'}</span></div>
          </div>
          <aside className="inspector">{app && version ? <><div className="inspector-heading"><AppIcon app={app} large/><div><h2>{app.title}</h2><p className="package" title={app.packageName}>{app.packageName}</p><span className="ready"><i className="dot"/>Ready to serve</span></div></div>
            <h3>Version history</h3><div className="version-list">{app.versions.map((v,i) => <div className="version-entry" key={v.sha256}><div className="version-title">{v.versionName || v.versionCode}{i === 0 && <span className="latest">Latest</span>}</div><p>Version code {v.versionCode} · {size(v.size)}</p><div className="version-file"><span title={v.filename}>{v.filename}</span><time>{date(v.addedAt)}</time></div></div>)}</div>
            <h3>Compatibility</h3><div className="info-row"><span>Platform</span><span>{version.tv ? 'TV' : 'Phone'}</span></div><div className="info-row"><span>Minimum SDK</span><span>API {version.minSdk}</span></div><div className="info-row"><span>Architecture</span><span>{version.abis.length ? version.abis.join(', ') : 'Universal'}</span></div>
            <button className="disclosure" onClick={() => setDetails(!details)}>File details{details ? <ChevronDown size={18}/> : <ChevronRight size={18}/>}</button>{details && <div className="file-details"><label>SHA-256</label><code>{version.sha256}</code><label>Signing certificate</label><code>{version.certificates.join('\n')}</code></div>}
            <button className="outline-button reveal" onClick={() => invoke(() => window.sibi.reveal(version.sha256))}><Folder size={18}/>Reveal in Finder</button>
          </> : <div className="inspector-placeholder"><FileBox size={32}/><p>Select an app to see its versions and file details.</p></div>}</aside>
        </div>
      </main>}
      {page === 'Transfers' && <main className="secondary-page"><h1>Transfers</h1><p className="muted">Downloads from your phone and TV</p><div className="transfer-list">{state?.transfers.map(t => <div className="transfer-row" key={t.id}><span className="transfer-icon"><ArrowDownToLine size={23}/></span><div><strong>{t.title}</strong><p>{t.device} · {date(t.startedAt)}</p></div><div className="transfer-progress"><progress max={t.size} value={t.bytes}/><small>{size(t.bytes)} / {size(t.size)}</small></div><span className={`transfer-state ${t.status}`}>{t.status}</span></div>)}{!state?.transfers.length && <div className="empty"><Download size={40}/><h2>No transfers yet</h2><p>Downloads will appear here when a device requests an APK.</p></div>}</div></main>}
      {page === 'Settings' && <main className="secondary-page"><h1>Settings</h1><p className="muted">Your library, on your network</p><section className="settings-card"><h3>Library folder</h3><p>{state?.folder}</p><p className="muted">APKs are served directly from this folder. No APK copies are stored by Sibi Store. Removing a file removes it from the library after scanning.</p><button className="outline-button" onClick={() => invoke(() => window.sibi.chooseFolder())}><Folder size={18}/>Choose folder</button></section><section className="settings-card"><h3>Local server</h3><div className="info-row"><span><i className={`dot ${state?.running ? '' : 'off'}`}/>{state?.running ? 'Running' : 'Stopped'}</span><button className="outline-button" onClick={() => invoke(() => window.sibi.server(!state?.running))}>{state?.running ? 'Stop server' : 'Start server'}</button></div><p className="muted">Phone and TV find this Mac automatically. You can also enter an address below.</p>{state?.addresses.map(a => <code className="address" key={a}>{a}</code>)}<button className="text-button" onClick={() => invoke(async () => { await window.sibi.copyAddress(); setCopied(true); setTimeout(() => setCopied(false), 2000); })}>{copied ? <Check size={16}/> : <Copy size={16}/>}{copied ? 'Copied' : 'Copy address'}</button>{state?.discoveryError && <p className="warning">Auto-discovery: {state.discoveryError}. Use the address above.</p>}</section><section className="settings-card"><h3>Startup</h3><label className="checkbox-row"><input type="checkbox" checked={state?.openAtLogin || false} onChange={e => invoke(() => window.sibi.login(e.target.checked))}/>Open Sibi Store when I log in</label><p className="muted">Closing the window keeps the server in the menu bar. Your Mac must be awake for devices to download.</p></section><section className="settings-card"><h3>APK tools</h3><p className="muted">Uses Android SDK Build Tools to read manifests and verify signatures.</p><code>{state?.sdk || 'Default Android SDK location'}</code><button className="outline-button" onClick={() => invoke(() => window.sibi.chooseSdk())}>Choose Android SDK</button></section></main>}
      <footer className="activity-bar">{active.length ? <><span className="circle-icon"><Download size={20}/></span><span>Serving {active[0].title} to {active[0].device}</span><progress max={active[0].size} value={active[0].bytes}/><span>{size(active[0].bytes)} / {size(active[0].size)}</span><button className="text-button activity-right" onClick={() => setPage('Transfers')}>{active.length} active {active.length === 1 ? 'transfer' : 'transfers'}<ChevronRight size={16}/></button></> : <><Monitor size={20}/><span>{state?.running ? 'Ready for your phone and TV' : 'Start the server to share your library'}</span><button className="text-button activity-right" onClick={() => setPage('Settings')}><CircleHelp size={16}/>{state?.running ? 'Connection details' : 'Server settings'}</button></>}</footer>
    </section>
  </div>;
}
createRoot(document.getElementById('root')!).render(<React.StrictMode><Desktop/></React.StrictMode>);
