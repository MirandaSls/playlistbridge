import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "PlaylistBridge — Move your listening",
  description: "A focused workspace for moving playlists from Spotify to YouTube.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
