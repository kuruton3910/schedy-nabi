import { SyncResponse } from "../types";

export const getMockSession = (
  username = "demo@ed.ritsumei.ac.jp"
): SyncResponse => {
  const now = new Date();
  const later = new Date(now.getTime() + 1000 * 60 * 60);

  return {
    username,
    syncedAt: now.toISOString(),
    nextClass: {
      courseName: "情報処理概論",
      day: "月",
      period: "2",
      location: "S棟201",
      startDateTime: now.toISOString(),
      endDateTime: later.toISOString(),
      untilStart: "0d 0h",
    },
    timetable: [
      {
        id: "c1",
        day: "月",
        period: "1",
        name: "英語リーディング",
        location: "L棟101",
        startTime: "09:00",
        endTime: "10:30",
        source: "AUTO",
      },
      {
        id: "c2",
        day: "月",
        period: "2",
        name: "情報処理概論",
        location: "S棟201",
        startTime: "10:45",
        endTime: "12:15",
        source: "AUTO",
      },
      {
        id: "c3",
        day: "火",
        period: "3",
        name: "数学演習",
        location: "M棟303",
        startTime: "13:00",
        endTime: "14:30",
        source: "MANUAL",
      },
    ],
    assignments: [
      {
        id: "a1",
        courseName: "情報処理概論",
        category: "レポート",
        title: "第2回課題：アルゴリズム",
        deadline: new Date(Date.now() + 1000 * 60 * 60 * 24).toISOString(),
        url: "",
      },
      {
        id: "a2",
        courseName: "英語リーディング",
        category: "小テスト",
        title: "リーディング演習",
        deadline: null,
        url: "",
      },
    ],
  };
};

export default getMockSession;
