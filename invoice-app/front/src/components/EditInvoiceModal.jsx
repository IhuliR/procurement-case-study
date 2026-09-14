import { useEffect, useState } from 'react';
import {
  Anchor,
  Button,
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
import StatusBadge from './StatusBadge.jsx';

const fields = ['invoice_number', 'supplier', 'purchase_request_number', 'invoice_sum', 'invoice_sum_paid', 'invoice_status'];

export default function EditInvoiceModal({ invoice, onClose, onUpdated }) {
  const [form, setForm] = useState(null);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  const {
    options: purchaseRequestOptions,
    status: purchaseRequestStatus,
  } = usePurchaseRequestOptions(!!invoice);

  const purchaseRequestData = purchaseRequestSelectData(purchaseRequestOptions);
  if (form?.purchase_request_number
      && !purchaseRequestOptions.some(
        ({ request_code }) => request_code === form.purchase_request_number
      )) {
    purchaseRequestData.unshift({
      value: form.purchase_request_number,
      label: `${form.purchase_request_number} — current linked request`,
    });
  }

  // Re-seed the form whenever the parent passes a new invoice in.
  useEffect(() => {
    if (invoice) {
      const seeded = {};
      fields.forEach((k) => { seeded[k] = invoice[k] ?? ''; });
      setForm(seeded);
      setError(null);
    } else {
      setForm(null);
    }
  }, [invoice]);

  if (!invoice || !form) return null;

  // See UploadInvoiceModal for the React-19 + StrictMode reasoning.
  const setField = (k) => (e) => {
    const value = typeof e === 'string' || typeof e === 'number'
      ? e
      : e?.currentTarget?.value ?? '';
    setForm((f) => ({ ...f, [k]: value }));
  };

  const selectPurchaseRequest = (requestCode) => {
    const option = purchaseRequestOptions.find(({ request_code }) => request_code === requestCode);
    if (!option) return;
    setForm((current) => ({
      ...current,
      purchase_request_number: option.request_code,
      supplier: option.supplier_name,
    }));
    setError(null);
  };

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    setSaving(true);
    try {
      // Send only the keys that actually changed — PUT is partial.
      const body = {};
      for (const k of fields) {
        if (form[k] !== '' && form[k] !== invoice[k]) body[k] = form[k];
      }
      if (Object.keys(body).length > 0) {
        await api.put(`/invoice/${invoice.id}`, body);
      }
      onUpdated?.();
      onClose();
    } catch (err) {
      setError(invoiceMutationError(err, 'Could not save changes'));
    } finally {
      setSaving(false);
    }
  };

  const downloadUrl = `${api.defaults.baseURL}/invoice/${invoice.id}/attachment`;

  return (
    <Modal
      opened={!!invoice}
      onClose={onClose}
      size="lg"
      centered
      title={
        <Group gap="sm">
          <Text fw={600} size="lg">{invoice.invoice_number}</Text>
          <StatusBadge status={form.invoice_status || invoice.invoice_status} />
        </Group>
      }
    >
      <form onSubmit={submit}>
        <Stack gap="md">
          {/* Attachment preview */}
          {invoice.attachment_filename ? (
            <Group
              justify="space-between"
              align="center"
              bg="gray.0"
              p="sm"
              style={{ borderRadius: 6 }}
            >
              <Group gap="sm" align="center">
                <Text size="xl">📎</Text>
                <Stack gap={0}>
                  <Text size="sm" fw={500}>{invoice.attachment_filename}</Text>
                  <Text size="xs" c="dimmed">
                    uploaded by {invoice.uploaded_by || 'unknown'} on{' '}
                    {invoice.created_at ? new Date(invoice.created_at).toLocaleDateString() : '—'}
                  </Text>
                </Stack>
              </Group>
              <Anchor href={downloadUrl} target="_blank" rel="noopener" size="sm">
                Download
              </Anchor>
            </Group>
          ) : (
            <Text size="sm" c="dimmed">No attachment uploaded.</Text>
          )}

          <Group grow align="flex-start">
            <TextInput
              label="Invoice number"
              value={form.invoice_number}
              onChange={setField('invoice_number')}
            />
            <TextInput
              label="Supplier"
              value={form.supplier}
              onChange={setField('supplier')}
              readOnly={Boolean(form.purchase_request_number)}
            />
          </Group>
          <Group grow align="flex-start">
            <Select
              label="Purchase request"
              value={form.purchase_request_number}
              placeholder={purchaseRequestStatus === 'loading'
                ? 'Loading approved requests…'
                : 'Select an approved request'}
              searchable
              data={purchaseRequestData}
              onChange={selectPurchaseRequest}
              disabled={saving
                || purchaseRequestStatus !== 'success'
                || purchaseRequestOptions.length === 0}
              nothingFoundMessage="No approved purchase requests"
            />
            <NumberInput
              label="Invoice sum"
              min={0}
              decimalScale={2}
              fixedDecimalScale
              value={form.invoice_sum}
              onChange={setField('invoice_sum')}
            />
            <NumberInput
              label="Paid"
              min={0}
              decimalScale={2}
              fixedDecimalScale
              value={form.invoice_sum_paid}
              onChange={setField('invoice_sum_paid')}
            />
          </Group>

          {purchaseRequestStatus === 'loading' && (
            <Text c="dimmed" size="sm">Loading approved purchase requests…</Text>
          )}
          {purchaseRequestStatus === 'success' && purchaseRequestOptions.length === 0 && (
            <Text c="dimmed" size="sm">
              No approved purchase requests are available. The current link is unchanged.
            </Text>
          )}
          {purchaseRequestStatus === 'error' && (
            <Text c="red" size="sm">
              Purchase requests could not be loaded. The current link is unchanged.
            </Text>
          )}

          <Stack gap={6}>
            <Text size="sm" fw={500} c="dark.7">Status</Text>
            <SegmentedControl
              fullWidth
              data={['created', 'prepaid', 'paid']}
              value={form.invoice_status}
              onChange={(v) => setForm((f) => ({ ...f, invoice_status: v }))}
            />
          </Stack>

          {form.purchase_request_number && (
            <Stack
              bg="rgba(34, 139, 230, 0.1)"
              p="sm"
              gap={4}
              style={{ borderRadius: 6 }}
            >
              <Text size="xs" fw={600} c="dark.7">Linked purchase request</Text>
              <Text size="sm" c="dark.7">{form.purchase_request_number}</Text>
              <Text size="xs" c="dimmed">
                Supplier is managed by the selected purchase request.
              </Text>
            </Stack>
          )}

          {error && <Text c="red" size="sm">{error}</Text>}

          <Group justify="flex-end">
            <Button variant="default" onClick={onClose} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" loading={saving}>
              Save changes
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
