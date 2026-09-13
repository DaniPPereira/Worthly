export function RisingW({ size = 28, onDark = true }: { size?: number; onDark?: boolean }) {
  const left = onDark ? "#F4F1EA" : "#0E4A3E";
  const right = "#C98F32";
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true">
      <path
        d="M7 14 15 34 24 20"
        stroke={left}
        strokeWidth="4.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M24 20 33 34 41 8"
        stroke={right}
        strokeWidth="4.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
