import { requestApi } from './api';

let activeLocale = 'en';
let translationCatalog: Record<string, string> = {};

export async function initLocalization(locale: string = 'en'): Promise<Record<string, string>> {
  activeLocale = locale;
  try {
    const res = await requestApi<{ locale: string; count: number; translations: Record<string, string> }>(
      `/localization/translations/${locale}`
    );
    if (res && res.translations) {
      translationCatalog = res.translations;
    }
  } catch (e) {
    console.warn('Unable to load dynamic translations, using cached fallback', e);
  }
  return translationCatalog;
}

export function t(key: string, fallbackText: string): string {
  if (translationCatalog && translationCatalog[key]) {
    return translationCatalog[key];
  }
  return fallbackText;
}

export function getActiveLocale(): string {
  return activeLocale;
}
