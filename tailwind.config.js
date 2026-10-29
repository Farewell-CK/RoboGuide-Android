/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        deaios: {
          dark: '#0a0d14',
          card: '#121722',
          border: '#1f293d',
          accent: '#3b82f6',
          success: '#10b981',
          warning: '#f59e0b',
          danger: '#ef4444',
          dog: '#38bdf8',
          agv: '#a855f7',
          cam: '#ec4899',
          human: '#10b981'
        }
      }
    },
  },
  plugins: [],
}
