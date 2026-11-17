import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { label } from '../constants';
import NotificationBell from './NotificationBell';

const NAV = {
  CITIZEN: [
    ['/dashboard', '🏠', 'My Dashboard'],
    ['/complaints', '📋', 'My Complaints'],
    ['/complaints/new', '➕', 'Report an Issue'],
    ['/track', '🔎', 'Track by ID'],
    ['/notifications', '🔔', 'Notifications'],
  ],
  OFFICER: [
    ['/dashboard', '🧰', 'My Work'],
    ['/complaints', '📋', 'Assigned Complaints'],
    ['/notifications', '🔔', 'Notifications'],
  ],
  ADMIN: [
    ['/dashboard', '📊', 'Dashboard & Reports'],
    ['/complaints', '📋', 'All Complaints'],
    ['/admin/departments', '🏢', 'Departments'],
    ['/admin/users', '👥', 'Users & Officers'],
    ['/notifications', '🔔', 'Notifications'],
  ],
};
const PORTAL = { CITIZEN: 'Citizen Portal', OFFICER: 'Officer Portal', ADMIN: 'Admin Console' };

export default function Layout() {
  const { user, logout } = useAuth();
  const nav = useNavigate();
  const [open, setOpen] = useState(false);

  return (
    <div className="app-shell" data-role={user.role}>
      <aside className={`sidebar ${open ? 'open' : ''}`}>
        <div className="brand">📍 CivicTrack<small>{PORTAL[user.role]}</small></div>
        <nav onClick={() => setOpen(false)}>
          {NAV[user.role].map(([to, icon, text]) => (
            <NavLink key={to} to={to} end={to === '/dashboard' || to === '/complaints'} className={({ isActive }) => (isActive ? 'active' : '')}>
              <span>{icon}</span> {text}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot">
          <div className="user-chip">
            <div className="avatar">{user.name.charAt(0).toUpperCase()}</div>
            <div>
              <strong>{user.name}</strong>
              <small>{label(user.role)}{user.departmentName ? ` · ${user.departmentName}` : ''}</small>
            </div>
          </div>
          <button className="btn btn-ghost btn-block" onClick={async () => { await logout(); nav('/login'); }}>Logout</button>
        </div>
      </aside>
      {open && <div className="backdrop" onClick={() => setOpen(false)} />}
      <div className="main">
        <header className="topbar">
          <button className="hamburger" onClick={() => setOpen(true)} aria-label="Open menu">☰</button>
          <span className="topbar-title">{PORTAL[user.role]}</span>
          <span className="spacer" />
          <NotificationBell />
        </header>
        <main className="content"><Outlet /></main>
      </div>
    </div>
  );
}
