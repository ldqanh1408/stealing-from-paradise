import Navbar, { type NavLink } from './Navbar';
import Footer from './Footer';

interface LayoutProps {
  children: React.ReactNode;
  appName: string;
  /** Always-visible nav links */
  links?: NavLink[];
  /** Nav links visible only when authenticated */
  authLinks?: NavLink[];
}

export default function Layout({ children, appName, links, authLinks }: LayoutProps) {
  return (
    <div className="flex flex-col min-h-screen">
      <Navbar appName={appName} links={links} authLinks={authLinks} />
      <main className="flex-1">
        {children}
      </main>
      <Footer appName={appName} />
    </div>
  );
}
