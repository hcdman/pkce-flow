import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";

function App() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch("/api/me", { credentials: "include" })
      .then((response) => response.ok ? response.json() : null)
      .then(setUser)
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p>Đang kiểm tra phiên đăng nhập...</p>;
  if (!user) {
    return <button onClick={() => { window.location.href = "/oauth2/login"; }}>
      Đăng nhập bằng Google
    </button>;
  }

  async function logout() {
    await fetch("/api/logout", { method: "POST", credentials: "include" });
    setUser(null);
  }

  return (
    <main>
      <h1>Xin chào, {user.name || user.email}</h1>
      <p>{user.email}</p>
      <button onClick={logout}>Đăng xuất</button>
    </main>
  );
}

createRoot(document.getElementById("root")).render(<App />);
