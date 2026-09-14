import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from './api.js';

/**
 * @typedef {Object} PurchaseRequestOption
 * @property {string} request_code
 * @property {string} request_name
 * @property {string} supplier_name
 */

export function usePurchaseRequestOptions(enabled) {
  const [options, setOptions] = useState([]);
  const [status, setStatus] = useState('idle');
  const requestIdRef = useRef(0);

  const load = useCallback(async () => {
    const requestId = ++requestIdRef.current;
    setStatus('loading');
    try {
      const response = await api.get('/invoice/purchase-request-options');
      if (!Array.isArray(response.data)) throw new Error('Invalid purchase request options');
      if (requestId !== requestIdRef.current) return;
      setOptions(response.data);
      setStatus('success');
    } catch {
      if (requestId !== requestIdRef.current) return;
      setOptions([]);
      setStatus('error');
    }
  }, []);

  useEffect(() => {
    if (enabled) {
      load();
    } else {
      requestIdRef.current += 1;
      setOptions([]);
      setStatus('idle');
    }
  }, [enabled, load]);

  return { options, status, reload: load };
}

export const purchaseRequestSelectData = (options) => options.map((option) => ({
  value: option.request_code,
  label: `${option.request_code} — ${option.request_name}`,
}));

export function invoiceMutationError(error, fallback) {
  const status = error?.response?.status;
  const backendMessage = error?.response?.data?.message;
  const safeMessages = new Set([
    'Purchase request is required',
    'Purchase request not found',
    'Purchase request is not approved',
    'Purchase request integration failed',
    'Purchase request service is unavailable',
    'Purchase request integration is not configured',
  ]);

  if (typeof backendMessage === 'string' && safeMessages.has(backendMessage)) {
    return backendMessage;
  }

  switch (status) {
    case 400:
      return 'Please check the invoice details and select a purchase request.';
    case 404:
      return 'The selected purchase request no longer exists. Please select another one.';
    case 409:
      return 'The selected purchase request is no longer approved. Please select another one.';
    case 502:
    case 503:
      return 'Purchase requests are temporarily unavailable. Please try again later.';
    default:
      return fallback;
  }
}
