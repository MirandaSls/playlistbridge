const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

try {
  const parsedApiBaseUrl = new URL(apiBaseUrl);
  if (!["http:", "https:"].includes(parsedApiBaseUrl.protocol)) {
    throw new Error("must use http:// or https://");
  }
} catch (error) {
  throw new Error(`NEXT_PUBLIC_API_BASE_URL must be an absolute HTTP(S) URL: ${error.message}`);
}

/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "standalone",
  env: {
    NEXT_PUBLIC_API_BASE_URL: apiBaseUrl,
  },
};

export default nextConfig;
