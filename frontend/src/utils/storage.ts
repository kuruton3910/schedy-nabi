// フロントエンド用データ管理ユーティリティ
// src/utils/storage.ts

import { CourseEntry, AssignmentEntry } from "../types";

export interface StoredData {
  timetable: CourseEntry[];
  assignments: AssignmentEntry[];
  nextClass: any;
  syncedAt: string;
  username: string;
  savedAt?: string;
}

const CACHE_DURATION = 24 * 60 * 60 * 1000; // 24時間

const SESSION_DATA_KEY = "schedyNabi_session_data";
const ENCRYPTED_DATA_KEY = "schedyNabi_secure_data_v1";

const SESSION_TOKEN_KEY = "schedyNabi_session_token_v1";
const ENCRYPTED_TOKEN_KEY_PREFIX = "schedyNabi_secure_token_v1_";

const SESSION_COOKIE_KEY_PREFIX = "schedyNabi_session_cookies_";
const ENCRYPTED_COOKIE_KEY_PREFIX = "schedyNabi_secure_cookie_v1_";

const LEGACY_DATA_KEY = "schedyNabi_data";
const LEGACY_COOKIE_KEY_PREFIX = "schedyNabi_cookies_";

const KEY_DB_NAME = "schedyNabi_secure_keys";
const KEY_STORE_NAME = "keys";
const KEY_ID = "primary";

const textEncoder =
  typeof TextEncoder !== "undefined" ? new TextEncoder() : null;
const textDecoder =
  typeof TextDecoder !== "undefined" ? new TextDecoder() : null;

type SafeStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

type CookiePayload = {
  cookies: Record<string, string>;
  savedAt: string;
};

type StoredToken = {
  username: string;
  token: string;
  expiresAt: string;
};

const createMemoryStorage = (): SafeStorage => {
  const store = new Map<string, string>();
  return {
    getItem(key: string) {
      return store.has(key) ? store.get(key)! : null;
    },
    setItem(key: string, value: string) {
      store.set(key, value);
    },
    removeItem(key: string) {
      store.delete(key);
    },
  };
};

const isBrowser = () => typeof window !== "undefined";

const resolveSessionStorage = (): SafeStorage => {
  if (!isBrowser()) {
    return createMemoryStorage();
  }
  try {
    return window.sessionStorage;
  } catch (error) {
    if (import.meta.env.DEV) {
      console.warn(
        "sessionStorage が利用できないため、メモリ上に保存します",
        error
      );
    }
    return createMemoryStorage();
  }
};

const getLocalStorage = (): Storage | null => {
  if (!isBrowser()) {
    return null;
  }
  try {
    return window.localStorage;
  } catch (error) {
    if (import.meta.env.DEV) {
      console.warn("localStorage が利用できません", error);
    }
    return null;
  }
};

const getIndexedDB = (): IDBFactory | null => {
  if (!isBrowser()) {
    return null;
  }
  return window.indexedDB ?? null;
};

const getCrypto = (): Crypto | null => {
  if (!isBrowser()) {
    return null;
  }
  return window.crypto ?? null;
};

const sessionStore = resolveSessionStorage();

const bufferToBase64 = (buffer: ArrayBuffer): string => {
  if (!isBrowser()) {
    return "";
  }
  const bytes = new Uint8Array(buffer);
  let binary = "";
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return window.btoa(binary);
};

const base64ToArrayBuffer = (base64: string): ArrayBuffer => {
  if (!isBrowser()) {
    return new ArrayBuffer(0);
  }
  const binary = window.atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
};

const openKeyDatabase = (): Promise<IDBDatabase> => {
  return new Promise((resolve, reject) => {
    const indexedDB = getIndexedDB();
    if (!indexedDB) {
      reject(new Error("IndexedDB が利用できません"));
      return;
    }

    const request = indexedDB.open(KEY_DB_NAME, 1);
    request.onerror = () => {
      reject(request.error ?? new Error("IndexedDB の初期化に失敗しました"));
    };
    request.onsuccess = () => {
      resolve(request.result);
    };
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(KEY_STORE_NAME)) {
        db.createObjectStore(KEY_STORE_NAME);
      }
    };
  });
};

const readStoredKeyData = async (): Promise<ArrayBuffer | null> => {
  try {
    const db = await openKeyDatabase();
    return await new Promise((resolve, reject) => {
      const transaction = db.transaction(KEY_STORE_NAME, "readonly");
      const store = transaction.objectStore(KEY_STORE_NAME);
      const request = store.get(KEY_ID);
      request.onerror = () =>
        reject(request.error ?? new Error("鍵の読み込みに失敗しました"));
      request.onsuccess = () => {
        const result = request.result;
        if (result instanceof ArrayBuffer) {
          resolve(result);
        } else if (result instanceof Uint8Array) {
          const clone = new Uint8Array(result.byteLength);
          clone.set(result);
          resolve(clone.buffer);
        } else if (typeof result === "string") {
          resolve(base64ToArrayBuffer(result));
        } else if (result == null) {
          resolve(null);
        } else {
          resolve(null);
        }
      };
      transaction.oncomplete = () => {
        db.close();
      };
    });
  } catch (error) {
    if (import.meta.env.DEV) {
      console.warn("暗号鍵の読み込みに失敗しました", error);
    }
    return null;
  }
};

const storeKeyData = async (data: ArrayBuffer): Promise<void> => {
  try {
    const db = await openKeyDatabase();
    await new Promise<void>((resolve, reject) => {
      const transaction = db.transaction(KEY_STORE_NAME, "readwrite");
      const store = transaction.objectStore(KEY_STORE_NAME);
      const request = store.put(data, KEY_ID);
      request.onerror = () =>
        reject(request.error ?? new Error("鍵の保存に失敗しました"));
      request.onsuccess = () => resolve();
      transaction.oncomplete = () => {
        db.close();
      };
    });
  } catch (error) {
    if (import.meta.env.DEV) {
      console.warn("暗号鍵の保存に失敗しました", error);
    }
  }
};

let cachedKeyPromise: Promise<CryptoKey | null> | null = null;

const getOrCreateEncryptionKey = async (): Promise<CryptoKey | null> => {
  if (!isBrowser()) {
    return null;
  }
  if (!cachedKeyPromise) {
    cachedKeyPromise = (async () => {
      const crypto = getCrypto();
      if (!crypto?.subtle || !textEncoder || !textDecoder) {
        if (import.meta.env.DEV) {
          console.warn("Web Crypto API または TextEncoder が利用できません");
        }
        return null;
      }

      try {
        let keyData = await readStoredKeyData();
        if (!keyData) {
          const generatedKey = await crypto.subtle.generateKey(
            { name: "AES-GCM", length: 256 },
            true,
            ["encrypt", "decrypt"]
          );
          keyData = await crypto.subtle.exportKey("raw", generatedKey);
          await storeKeyData(keyData);
        }
        return crypto.subtle.importKey(
          "raw",
          keyData,
          { name: "AES-GCM" },
          false,
          ["encrypt", "decrypt"]
        );
      } catch (error) {
        if (import.meta.env.DEV) {
          console.error("暗号鍵の初期化に失敗しました", error);
        }
        return null;
      }
    })();
  }
  return cachedKeyPromise;
};

const encryptPayload = async (data: unknown): Promise<string | null> => {
  const crypto = getCrypto();
  if (!crypto?.subtle || !textEncoder) {
    return null;
  }
  const key = await getOrCreateEncryptionKey();
  if (!key) {
    return null;
  }
  try {
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const encoded = textEncoder.encode(JSON.stringify(data));
    const encrypted = await crypto.subtle.encrypt(
      { name: "AES-GCM", iv },
      key,
      encoded
    );
    const payload = new Uint8Array(iv.length + encrypted.byteLength);
    payload.set(iv, 0);
    payload.set(new Uint8Array(encrypted), iv.length);
    return bufferToBase64(payload.buffer);
  } catch (error) {
    if (import.meta.env.DEV) {
      console.error("データの暗号化に失敗しました", error);
    }
    return null;
  }
};

const decryptPayload = async <T>(payload: string): Promise<T | null> => {
  const crypto = getCrypto();
  if (!crypto?.subtle || !textDecoder) {
    return null;
  }
  const key = await getOrCreateEncryptionKey();
  if (!key) {
    return null;
  }
  try {
    const raw = base64ToArrayBuffer(payload);
    if (raw.byteLength <= 12) {
      return null;
    }
    const iv = new Uint8Array(raw.slice(0, 12));
    const data = new Uint8Array(raw.slice(12));
    const decrypted = await crypto.subtle.decrypt(
      { name: "AES-GCM", iv },
      key,
      data
    );
    const json = textDecoder.decode(decrypted);
    return JSON.parse(json) as T;
  } catch (error) {
    if (import.meta.env.DEV) {
      console.error("データの復号に失敗しました", error);
    }
    return null;
  }
};

const writeEncrypted = async (key: string, data: unknown): Promise<void> => {
  const storage = getLocalStorage();
  if (!storage) {
    return;
  }
  const payload = await encryptPayload(data);
  if (!payload) {
    return;
  }
  try {
    storage.setItem(key, payload);
  } catch (error) {
    if (import.meta.env.DEV) {
      console.error("暗号化データの保存に失敗しました", error);
    }
  }
};

const readEncrypted = async <T>(key: string): Promise<T | null> => {
  const storage = getLocalStorage();
  if (!storage) {
    return null;
  }
  const payload = storage.getItem(key);
  if (!payload) {
    return null;
  }
  const data = await decryptPayload<T>(payload);
  if (!data) {
    storage.removeItem(key);
    return null;
  }
  return data;
};

const removeEncrypted = async (key: string): Promise<void> => {
  const storage = getLocalStorage();
  if (!storage) {
    return;
  }
  storage.removeItem(key);
};

const isExpired = (data: StoredData): boolean => {
  if (!data.savedAt) {
    return false;
  }
  const savedAt = new Date(data.savedAt).getTime();
  if (Number.isNaN(savedAt)) {
    return false;
  }
  return Date.now() - savedAt > CACHE_DURATION;
};

const purgeLegacyLocalStorageArtifacts = () => {
  const storage = getLocalStorage();
  if (!storage) {
    return;
  }
  try {
    storage.removeItem(LEGACY_DATA_KEY);
    for (let i = storage.length - 1; i >= 0; i -= 1) {
      const key = storage.key(i);
      if (!key) continue;
      if (key.startsWith(LEGACY_COOKIE_KEY_PREFIX)) {
        storage.removeItem(key);
      }
    }
  } catch (error) {
    if (import.meta.env.DEV) {
      console.warn("旧フォーマットの削除に失敗しました", error);
    }
  }
};

purgeLegacyLocalStorageArtifacts();

export const saveDataToStorage = async (data: StoredData): Promise<void> => {
  const storageData: StoredData = {
    ...data,
    savedAt: new Date().toISOString(),
  };
  try {
    sessionStore.setItem(SESSION_DATA_KEY, JSON.stringify(storageData));
  } catch (error) {
    if (import.meta.env.DEV) {
      console.warn("セッションへのデータ保存に失敗しました", error);
    }
  }
  await writeEncrypted(ENCRYPTED_DATA_KEY, storageData);
};

export const saveAuthToken = async (
  record: StoredToken,
  persist: boolean
): Promise<void> => {
  try {
    sessionStore.setItem(SESSION_TOKEN_KEY, JSON.stringify(record));
  } catch (error) {
    if (import.meta.env.DEV) {
      console.warn("セッショントークンの保存に失敗しました", error);
    }
  }

  if (persist) {
    await writeEncrypted(
      `${ENCRYPTED_TOKEN_KEY_PREFIX}${record.username}`,
      record
    );
  } else {
    await removeEncrypted(`${ENCRYPTED_TOKEN_KEY_PREFIX}${record.username}`);
  }
};

export const loadAuthToken = async (
  username: string
): Promise<StoredToken | null> => {
  try {
    const sessionValue = sessionStore.getItem(SESSION_TOKEN_KEY);
    if (sessionValue) {
      const parsed = JSON.parse(sessionValue) as StoredToken;
      if (parsed.username === username) {
        return parsed;
      }
    }
  } catch (error) {
    if (import.meta.env.DEV) {
      console.warn("セッショントークンの読み込みに失敗しました", error);
    }
  }

  const stored = await readEncrypted<StoredToken>(
    `${ENCRYPTED_TOKEN_KEY_PREFIX}${username}`
  );
  if (stored) {
    await saveAuthToken(stored, true);
    return stored;
  }
  return null;
};

export const clearAuthToken = async (username?: string): Promise<void> => {
  sessionStore.removeItem(SESSION_TOKEN_KEY);
  if (username) {
    await removeEncrypted(`${ENCRYPTED_TOKEN_KEY_PREFIX}${username}`);
  }
};

export const loadDataFromStorage = async (): Promise<StoredData | null> => {
  try {
    const cached = sessionStore.getItem(SESSION_DATA_KEY);
    if (cached) {
      const parsed = JSON.parse(cached) as StoredData;
      if (!isExpired(parsed)) {
        return parsed;
      }
      sessionStore.removeItem(SESSION_DATA_KEY);
    }

    const decrypted = await readEncrypted<StoredData>(ENCRYPTED_DATA_KEY);
    if (!decrypted) {
      return null;
    }
    if (isExpired(decrypted)) {
      await removeEncrypted(ENCRYPTED_DATA_KEY);
      return null;
    }
    try {
      sessionStore.setItem(SESSION_DATA_KEY, JSON.stringify(decrypted));
    } catch (error) {
      if (import.meta.env.DEV) {
        console.warn("セッションへのデータ復元に失敗しました", error);
      }
    }
    return decrypted;
  } catch (error) {
    if (import.meta.env.DEV) {
      console.error("データ読み込みエラー:", error);
    }
    return null;
  }
};

export const clearStorage = async (): Promise<void> => {
  try {
    sessionStore.removeItem(SESSION_DATA_KEY);
  } catch (error) {
    if (import.meta.env.DEV) {
      console.warn("セッションデータの削除に失敗しました", error);
    }
  }
  await removeEncrypted(ENCRYPTED_DATA_KEY);
};

export const isDataFresh = async (): Promise<boolean> => {
  const data = await loadDataFromStorage();
  if (!data || !data.savedAt) {
    return false;
  }
  const savedAt = new Date(data.savedAt).getTime();
  if (Number.isNaN(savedAt)) {
    return false;
  }
  const hoursSinceSync = (Date.now() - savedAt) / (1000 * 60 * 60);
  return hoursSinceSync < 6;
};

export const addManualCourse = async (
  course: Omit<CourseEntry, "id" | "source">
): Promise<void> => {
  const data = await loadDataFromStorage();
  if (!data) {
    return;
  }
  const newCourse: CourseEntry = {
    ...course,
    id: `manual-${Date.now()}`,
    source: "MANUAL",
  };
  data.timetable.push(newCourse);
  await saveDataToStorage(data);
};

export const removeManualCourse = async (courseId: string): Promise<void> => {
  const data = await loadDataFromStorage();
  if (!data) {
    return;
  }
  data.timetable = data.timetable.filter((course) => course.id !== courseId);
  await saveDataToStorage(data);
};

export const getDataStats = async (): Promise<{
  totalCourses: number;
  autoCourses: number;
  manualCourses: number;
  totalAssignments: number;
  overdueAssignments: number;
  lastSync: string | null;
}> => {
  const data = await loadDataFromStorage();
  if (!data) {
    return {
      totalCourses: 0,
      autoCourses: 0,
      manualCourses: 0,
      totalAssignments: 0,
      overdueAssignments: 0,
      lastSync: null,
    };
  }

  const now = new Date();
  const overdueAssignments = data.assignments.filter((assignment) => {
    if (!assignment.deadline) return false;
    const deadline = new Date(assignment.deadline);
    return deadline < now;
  }).length;

  return {
    totalCourses: data.timetable.length,
    autoCourses: data.timetable.filter((c) => c.source === "AUTO").length,
    manualCourses: data.timetable.filter((c) => c.source === "MANUAL").length,
    totalAssignments: data.assignments.length,
    overdueAssignments,
    lastSync: data.syncedAt,
  };
};

export const isOnline = (): boolean => {
  if (!isBrowser()) {
    return true;
  }
  return navigator.onLine;
};

export const exportData = async (): Promise<string> => {
  const data = await loadDataFromStorage();
  if (!data) {
    return "{}";
  }
  return JSON.stringify(data, null, 2);
};

export const importData = async (jsonString: string): Promise<boolean> => {
  try {
    const data = JSON.parse(jsonString);
    await saveDataToStorage(data);
    return true;
  } catch (error) {
    if (import.meta.env.DEV) {
      console.error("データインポートエラー:", error);
    }
    return false;
  }
};

const storeCookies = async (key: string, payload: CookiePayload) => {
  try {
    sessionStore.setItem(key, JSON.stringify(payload));
  } catch (error) {
    if (import.meta.env.DEV) {
      console.warn("セッションへのクッキー保存に失敗しました", error);
    }
  }
};

const readCookiesFromSession = (key: string): Record<string, string> | null => {
  const raw = sessionStore.getItem(key);
  if (!raw) {
    return null;
  }
  try {
    const payload = JSON.parse(raw) as CookiePayload;
    if (!payload || typeof payload !== "object" || !payload.cookies) {
      return null;
    }
    return payload.cookies;
  } catch (error) {
    if (import.meta.env.DEV) {
      console.error("クッキー読み込みエラー:", error);
    }
    return null;
  }
};

export const saveCookiesForUser = async (
  username: string,
  cookies: Record<string, string>,
  options?: { persist?: boolean }
): Promise<void> => {
  if (!username) {
    return;
  }
  const payload: CookiePayload = {
    cookies,
    savedAt: new Date().toISOString(),
  };
  await storeCookies(`${SESSION_COOKIE_KEY_PREFIX}${username}`, payload);
  if (options?.persist) {
    await writeEncrypted(`${ENCRYPTED_COOKIE_KEY_PREFIX}${username}`, payload);
  } else {
    await removeEncrypted(`${ENCRYPTED_COOKIE_KEY_PREFIX}${username}`);
  }
};

export const loadCookiesForUser = async (
  username: string
): Promise<Record<string, string> | null> => {
  if (!username) {
    return null;
  }
  const sessionCookies = readCookiesFromSession(
    `${SESSION_COOKIE_KEY_PREFIX}${username}`
  );
  if (sessionCookies) {
    return sessionCookies;
  }
  const payload = await readEncrypted<CookiePayload>(
    `${ENCRYPTED_COOKIE_KEY_PREFIX}${username}`
  );
  if (!payload) {
    return null;
  }
  await storeCookies(`${SESSION_COOKIE_KEY_PREFIX}${username}`, payload);
  return payload.cookies;
};

export const deleteCookiesForUser = async (username: string): Promise<void> => {
  if (!username) {
    return;
  }
  sessionStore.removeItem(`${SESSION_COOKIE_KEY_PREFIX}${username}`);
  await removeEncrypted(`${ENCRYPTED_COOKIE_KEY_PREFIX}${username}`);
};
