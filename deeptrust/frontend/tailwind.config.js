/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        deeptrust: {
          bg: "#0b0f14",
          panel: "#121821",
          accent: "#22d3ee",
          danger: "#f87171",
          success: "#34d399",
          warning: "#fbbf24",
        },
      },
    },
  },
  plugins: [],
};
