interface ErrorBannerProps {
  message: string;
  details?: string[];
}

const ErrorBanner = ({ message, details }: ErrorBannerProps) => {
  return (
    <div className="error-banner">
      <strong>エラー:</strong> {message}
      {details && details.length > 0 ? (
        <ul style={{ marginTop: "0.75rem", paddingLeft: "1.25rem" }}>
          {details.map((detail) => (
            <li key={detail}>{detail}</li>
          ))}
        </ul>
      ) : null}
    </div>
  );
};

export default ErrorBanner;
