import axios from 'axios';

// Backend URL — defaults to the dev server port. In Docker Compose we pass
// VITE_API_URL at build/start time.
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8001',
  withCredentials: true, // send the pr_token cookie on every request
});

export const getPurchaseRequestInvoices = (requestCode, config = {}) =>
  api.get(
    `/purchase-request/${encodeURIComponent(requestCode)}/invoices`,
    config
  );
