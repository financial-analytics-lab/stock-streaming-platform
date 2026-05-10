/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        surface: '#0F1117',
        card: '#1A1D27',
        border: '#2D3148',
        positive: '#00D4AA',
        negative: '#FF4560',
        neutral: '#F5A623',
        'text-primary': '#E2E8F0',
        muted: '#64748B',
      },
      animation: {
        ticker: 'ticker 40s linear infinite',
      },
      keyframes: {
        ticker: {
          '0%': { transform: 'translateX(0)' },
          '100%': { transform: 'translateX(-50%)' },
        },
      },
    },
  },
  plugins: [],
}
