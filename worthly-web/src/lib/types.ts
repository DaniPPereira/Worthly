export type Owner = {
  id: string;
  email: string;
  reportingTimezone: string;
  reportingCurrency: string;
};

export type Connection = {
  id: string;
  provider: string;
  status: string;
  institutionName: string | null;
  institutionCountry: string | null;
  lastSuccessfulSyncAt: string | null;
  consentExpiresAt: string | null;
};

export type BankChoice = {
  name: string;
  country: string;
  logoUrl: string | null;
};

export type SyncRun = {
  id: string;
  status: string;
  startedAt: string;
  finishedAt: string | null;
  importedCount: number | null;
  updatedCount: number | null;
  nextRetryAt: string | null;
  errorCode: string | null;
};

export type SyncRunPage = {
  items: SyncRun[];
  page: number;
  size: number;
  total: number;
};

export type Account = {
  id: string;
  provider: string;
  displayName: string;
  type: string;
  currency: string;
  maskedIdentifier: string | null;
  active: boolean;
  includedInLiquidCash: boolean;
  includedInNetWorth: boolean;
};

export type Balance = {
  type: string;
  money: { amount: string; currency: string };
  observedAt: string;
  usedForLiquidCash: boolean;
};

export type Transaction = {
  id: string;
  accountId: string;
  direction: "CREDIT" | "DEBIT";
  lifecycleStatus: string;
  economicType: string;
  money: { amount: string; currency: string };
  merchant: string | null;
  description: string | null;
  location: string | null;
  reportingAt: string;
  categoryId: string | null;
  notes: string | null;
  transferMatchId: string | null;
};

export type TransactionPage = {
  items: Transaction[];
  page: number;
  size: number;
  total: number;
};

export type Category = {
  id: string;
  code: string | null;
  label: string;
  parentId: string | null;
  system: boolean;
};

export type CategorizationRule = {
  id: string;
  priority: number;
  field: string;
  operator: string;
  matchValue: string;
  amountMin: string | null;
  amountMax: string | null;
  targetCategoryId: string;
  enabled: boolean;
};

export type TransferMatch = {
  id: string;
  leftTransactionId: string;
  rightTransactionId: string;
  confidence: number;
  method: string;
  status: string;
};

export type WealthSummary = {
  asOf: string;
  timezone: string;
  totalsByCurrency: Array<{
    currency: string;
    liquidCash: string;
    investmentValue: string;
    netWorth: string;
  }>;
};

export type MonthlyAnalytics = {
  month: string;
  timezone: string;
  totalsByCurrency: Array<{
    currency: string;
    income: string;
    expenses: string;
    invested: string;
    savings: string;
    savingsRate: string | null;
    savingsRateReason: string | null;
  }>;
};

export type InvestmentSummary = {
  observedAt: string | null;
  totalsByCurrency: Array<{
    currency: string;
    cash: string;
    portfolioValue: string;
  }>;
};

export type Position = {
  instrumentKey: string;
  ticker: string | null;
  quantity: string | null;
  marketValue: { amount: string; currency: string } | null;
  observedAt: string | null;
};

export type Notification = {
  id: string;
  type: string;
  createdAt: string;
  readAt: string | null;
};

export type Device = {
  id: string;
  name: string | null;
  platform: string | null;
  createdAt: string;
  lastSeenAt: string | null;
  revoked: boolean;
};
