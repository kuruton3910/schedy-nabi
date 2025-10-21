interface SuccessBannerProps {
  message: string;
}

const SuccessBanner = ({ message }: SuccessBannerProps) => {
  return <div className="success-banner">{message}</div>;
};

export default SuccessBanner;
