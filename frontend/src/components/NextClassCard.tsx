import { NextClassCard as NextClassCardType } from "../types";
import { describeDuration, formatDateTime } from "../utils/format";

interface Props {
  nextClass: NextClassCardType | null;
}

const NextClassCard = ({ nextClass }: Props) => {
  if (!nextClass) {
    return (
      <div className="next-class-card">
        <span className="title">次の授業は登録されていません</span>
        <span className="text-muted">
          時間割を同期するか、手動で授業を追加してください。
        </span>
      </div>
    );
  }

  return (
    <div className="next-class-card">
      <span className="title">{`${nextClass.day} ${nextClass.period}限 ${nextClass.courseName}`}</span>
      <div className="tag">
        開始まで {describeDuration(nextClass.untilStart)}
      </div>
      <div>
        <strong>開始:</strong> {formatDateTime(nextClass.startDateTime)}
      </div>
      <div>
        <strong>終了:</strong> {formatDateTime(nextClass.endDateTime)}
      </div>
      <div>
        <strong>教室:</strong> {nextClass.location || "未設定"}
      </div>
    </div>
  );
};

export default NextClassCard;
