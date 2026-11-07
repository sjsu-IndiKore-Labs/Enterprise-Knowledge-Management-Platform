/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Smaller Docker image: `node server.js` from .next/standalone (see Dockerfile).
  output: 'standalone',
};

export default nextConfig;
