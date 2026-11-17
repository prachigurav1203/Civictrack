import { useAuth } from '../context/AuthContext';
import AdminDashboard from './AdminDashboard';
import CitizenDashboard from './CitizenDashboard';
import OfficerDashboard from './OfficerDashboard';

/** Three separate dashboards, one per role. */
export default function Dashboard() {
  const { user } = useAuth();
  if (user.role === 'ADMIN') return <AdminDashboard />;
  if (user.role === 'OFFICER') return <OfficerDashboard />;
  return <CitizenDashboard />;
}
