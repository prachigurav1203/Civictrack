import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import Protected from './components/Protected';
import Layout from './components/Layout';
import Login from './pages/Login';
import Register from './pages/Register';
import Track from './pages/Track';
import Dashboard from './pages/Dashboard';
import Complaints from './pages/Complaints';
import NewComplaint from './pages/NewComplaint';
import ComplaintDetail from './pages/ComplaintDetail';
import Notifications from './pages/Notifications';
import Departments from './pages/Departments';
import Users from './pages/Users';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/track" element={<Track />} />
          <Route element={<Protected><Layout /></Protected>}>
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/complaints" element={<Complaints />} />
            <Route path="/complaints/new" element={<Protected roles={['CITIZEN']}><NewComplaint /></Protected>} />
            <Route path="/complaints/:id" element={<ComplaintDetail />} />
            <Route path="/notifications" element={<Notifications />} />
            <Route path="/admin/departments" element={<Protected roles={['ADMIN']}><Departments /></Protected>} />
            <Route path="/admin/users" element={<Protected roles={['ADMIN']}><Users /></Protected>} />
          </Route>
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
