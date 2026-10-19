export default function BrandIcon({ className = '' }) {
  return (
    <img
      className={`chatgpt-brand-icon${className ? ` ${className}` : ''}`}
      src="/chatgpt-icon.svg"
      alt=""
      aria-hidden="true"
    />
  )
}
