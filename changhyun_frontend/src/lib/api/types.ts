import type { components } from './schema';

type S = components['schemas'];

export type DepartmentCode = S['DepartmentCode'];
export type Role = S['Role'];
export type DocumentStatus = S['DocumentStatus'];
export type DocumentCategory = S['DocumentCategory'];
export type Department = S['Department'];
export type CurrentUser = S['CurrentUser'];
export type LoginResponse = S['LoginResponse'];
export type Document = S['Document'];
export type DocumentDetail = S['DocumentDetail'];
export type DocumentVersion = S['DocumentVersion'];
export type DocumentPage = S['DocumentPage'];
export type DownloadUrl = S['DownloadUrl'];
export type Chunk = S['Chunk'];
export type SearchMode = S['SearchMode'];
export type SearchResult = S['SearchResult'];
export type SearchResponse = S['SearchResponse'];
export type Citation = S['Citation'];
export type ChatRequest = S['ChatRequest'];
export type ChatMessage = S['ChatMessage'];
export type Conversation = S['Conversation'];
export type ConversationDetail = S['ConversationDetail'];
export type ConversationPage = S['ConversationPage'];
export type FeedbackRating = S['FeedbackRequest']['rating'];
export type Permission = NonNullable<CurrentUser['permissions']>[number];

/** Values mirror the enums in contracts/openapi/core-api.yaml. */
export const DEPARTMENT_CODES: DepartmentCode[] = [
  'GENERAL',
  'HR',
  'ENGINEERING',
  'FINANCE',
  'LEGAL',
  'MARKETING',
];
export const CATEGORIES: DocumentCategory[] = ['POLICY', 'SOP', 'MANUAL', 'GUIDE', 'REPORT', 'OTHER'];
export const STATUSES: DocumentStatus[] = ['UPLOADED', 'QUEUED', 'PROCESSING', 'INDEXED', 'FAILED'];
export const SEARCH_MODES: SearchMode[] = ['HYBRID', 'SEMANTIC', 'KEYWORD'];

export const isTerminalStatus = (s: DocumentStatus): boolean => s === 'INDEXED' || s === 'FAILED';
