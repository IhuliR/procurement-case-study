import { useRef, useState } from 'react';
import {
  Button,
  FileButton,
  Group,
  Modal,
  NumberInput,
  SegmentedControl,
  Select,
  Stack,
  Text,
  TextInput,
} from '@mantine/core';
import { api } from '../api.js';
import {
  invoiceMutationError,
  purchaseRequestSelectData,
  usePurchaseRequestOptions,
} from '../purchaseRequests.js';

const EMPTY = {
  invoice_number: '',
  supplier: '',
  purchase_request_number: '',
  invoice_sum: '',
  invoice_sum_paid: '',
  invoice_status: 'created',
};

export default function UploadInvoiceModal({ opened, onClose, onCreated }) {
  const [form, setForm] = useState(EMPTY);
  const [file, setFile] = useState(null);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const resetRef = useRef(null);
  const {
    options: purchaseRequestOptions,
    status: purchaseRequestStatus,
    reload: reloadPurchaseRequests,
  } = usePurchaseRequestOptions(opened);

  // Read value synchronously before scheduling the state update — React 19
  // + StrictMode invokes functional updaters twice and `e.currentTarget`
  // is nulled out between calls.
  const setField = (k) => (e) => {
    const value = typeof e === 'string' || typeof e === 'number'
      ? e
      : e?.currentTarget?.value ?? '';
    setForm((f) => ({ ...f, [k]: value }));
  };

  const close = () => {
    setForm(EMPTY);
    setFile(null);
    setError(null);
    resetRef.current?.();
    onClose();
  };

  const selectPurchaseRequest = (requestCode) => {
    const option = purchaseRequestOptions.find(({ request_code }) => request_code === requestCode);
    setForm((current) => ({
      ...current,
      purchase_request_number: option?.request_code || '',
      supplier: option?.supplier_name || '',
    }));
    setError(null);
  };

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    const selectedPurchaseRequest = purchaseRequestOptions.find(
      ({ request_code }) => request_code === form.purchase_request_number
    );
    if (!selectedPurchaseRequest) {
      setError('Select an approved purchase request.');
      return;
    }
    setSubmitting(true);
    try {
      const fd = new FormData();
      fd.append('invoice_number', form.invoice_number);
      fd.append('supplier', selectedPurchaseRequest.supplier_name);
      fd.append('purchase_request_number', selectedPurchaseRequest.request_code);
      fd.append('invoice_sum', form.invoice_sum);
      if (form.invoice_sum_paid !== '' && form.invoice_sum_paid != null)
        fd.append('invoice_sum_paid', form.invoice_sum_paid);
      fd.append('invoice_status', form.invoice_status);
      if (file) fd.append('attachment', file);

      await api.post('/invoice', fd);
      onCreated?.();
      close();
    } catch (err) {
      setError(invoiceMutationError(err, 'Could not upload invoice'));
      if (err?.response?.status === 404 || err?.response?.status === 409) {
        setForm((current) => ({
          ...current,
          purchase_request_number: '',
          supplier: '',
        }));
        await reloadPurchaseRequests();
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      opened={opened}
      onClose={close}
      title={<Text fw={600} size="lg">Upload Invoice</Text>}
      size="lg"
      centered
    >
      <form onSubmit={submit}>
        <Stack gap="md">
          {/* PDF drop zone (clickable Mantine FileButton with a styled label) */}
          <FileButton
            resetRef={resetRef}
            accept="application/pdf"
            onChange={setFile}
          >
            {(props) => (
              <Stack
                {...props}
                bg="gray.0"
                p="lg"
                gap={4}
                align="center"
                style={{
                  borderRadius: 6,
                  border: '1px dashed var(--mantine-color-gray-4)',
                  cursor: 'pointer',
                }}
              >
                <Text size="xl">📄</Text>
                <Text size="sm" fw={500} c="dark.6">
                  {file ? file.name : 'Click to choose invoice PDF'}
                </Text>
                <Text size="xs" c="dimmed">PDF only · max 10 MB</Text>
              </Stack>
            )}
          </FileButton>

          <TextInput
            label="Invoice number"
            placeholder="e.g. INV-2026-0512"
            required
            value={form.invoice_number}
            onChange={setField('invoice_number')}
          />
          <Group grow align="flex-start">
            <TextInput
              label="Supplier"
              placeholder="Set by purchase request"
              required
              value={form.supplier}
              readOnly
            />
            <Select
              label="Purchase request"
              placeholder={purchaseRequestStatus === 'loading'
                ? 'Loading approved requests…'
                : 'Select an approved request'}
              required
              searchable
              data={purchaseRequestSelectData(purchaseRequestOptions)}
              value={form.purchase_request_number}
              onChange={selectPurchaseRequest}
              disabled={purchaseRequestStatus !== 'success' || submitting}
              nothingFoundMessage="No approved purchase requests"
            />
          </Group>
          {purchaseRequestStatus === 'loading' && (
            <Text c="dimmed" size="sm">Loading approved purchase requests…</Text>
          )}
          {purchaseRequestStatus === 'success' && purchaseRequestOptions.length === 0 && (
            <Text c="dimmed" size="sm">No approved purchase requests are available.</Text>
          )}
          {purchaseRequestStatus === 'error' && (
            <Text c="red" size="sm">
              Purchase requests are temporarily unavailable. Please try again later.
            </Text>
          )}
          <Group grow align="flex-start">
            <NumberInput
              label="Invoice sum"
              placeholder="11400.00"
              required
              min={0}
              decimalScale={2}
              fixedDecimalScale
              value={form.invoice_sum}
              onChange={setField('invoice_sum')}
            />
            <NumberInput
              label="Paid so far"
              placeholder="0.00"
              min={0}
              decimalScale={2}
              fixedDecimalScale
              value={form.invoice_sum_paid}
              onChange={setField('invoice_sum_paid')}
            />
          </Group>

          <Stack gap={6}>
            <Text size="sm" fw={500} c="dark.7">Initial status</Text>
            <SegmentedControl
              fullWidth
              data={['created', 'prepaid', 'paid']}
              value={form.invoice_status}
              onChange={(v) => setForm((f) => ({ ...f, invoice_status: v }))}
            />
          </Stack>

          {error && <Text c="red" size="sm">{error}</Text>}

          <Group justify="flex-end">
            <Button variant="default" onClick={close} disabled={submitting}>
              Cancel
            </Button>
            <Button
              type="submit"
              loading={submitting}
              disabled={purchaseRequestStatus !== 'success'
                || purchaseRequestOptions.length === 0
                || !form.purchase_request_number
                || submitting}
            >
              Upload invoice
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
