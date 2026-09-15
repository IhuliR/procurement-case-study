import { useEffect, useState } from 'react';
import {
  Badge,
  Button,
  Card,
  Divider,
  Group,
  Loader,
  Modal,
  SimpleGrid,
  Stack,
  Text,
} from '@mantine/core';
import { api, getPurchaseRequestInvoices } from '../api.js';
import { useAuth } from '../auth.jsx';
import StatusBadge from './StatusBadge.jsx';

const FieldRow = ({ label, value }) => (
  <Stack gap={2}>
    <Text size="xs" fw={500} c="dimmed">
      {label}
    </Text>
    <Text size="md" c="dark.7" style={{ whiteSpace: 'pre-wrap' }}>
      {value}
    </Text>
  </Stack>
);

const formatAmount = (value) => {
  if (value === null || value === undefined || value === '') return '—';

  const amount = Number(value);
  return Number.isFinite(amount)
    ? new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(amount)
    : '—';
};

export default function ViewPRModal({ pr, onClose, onUpdated }) {
  const { user } = useAuth();
  const [updating, setUpdating] = useState(false);
  const [error, setError] = useState(null);
  const [invoiceState, setInvoiceState] = useState({
    status: 'idle',
    items: [],
    message: null,
    requestCode: null,
  });

  useEffect(() => {
    if (!pr?.request_code) {
      setInvoiceState({
        status: 'idle',
        items: [],
        message: null,
        requestCode: null,
      });
      return undefined;
    }

    let active = true;
    const controller = new AbortController();
    const requestCode = pr.request_code;

    setInvoiceState({
      status: 'loading',
      items: [],
      message: null,
      requestCode,
    });
    getPurchaseRequestInvoices(requestCode, { signal: controller.signal })
      .then((response) => {
        if (!active) return;

        if (!Array.isArray(response.data)) {
          setInvoiceState({
            status: 'error',
            items: [],
            message: 'Could not load invoice information',
            requestCode,
          });
          return;
        }

        setInvoiceState({
          status: 'success',
          items: response.data,
          message: null,
          requestCode,
        });
      })
      .catch((invoiceError) => {
        if (!active || controller.signal.aborted) return;

        const isTemporarilyUnavailable = [502, 503].includes(
          invoiceError?.response?.status
        );
        setInvoiceState({
          status: 'error',
          items: [],
          message: isTemporarilyUnavailable
            ? 'Invoice information is temporarily unavailable'
            : 'Could not load invoice information',
          requestCode,
        });
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [pr?.request_code]);

  if (!pr) return null;

  const invoiceStatus =
    invoiceState.requestCode === pr.request_code
      ? invoiceState.status
      : 'loading';

  // --- Role-aware action gating ---
  // Only the request author can send "initiated" → "sent for approval".
  // Only finance users can approve/reject when status is "sent for approval".
  // Once approved or rejected, no more transitions.
  const isAuthor = user?.username === pr.request_author;
  const isFinance = user?.role === 'finance';
  const status = pr.request_approval_status;

  const canSendForApproval = isAuthor && status === 'initiated';
  const canApproveOrReject = isFinance && status === 'sent for approval';

  const transition = async (newStatus) => {
    setError(null);
    setUpdating(true);
    try {
      await api.put(`/purchase-request/${pr.id}`, {
        request_approval_status: newStatus,
      });
      onUpdated?.();
      onClose();
    } catch (err) {
      setError(err?.response?.data?.detail || 'Could not update status');
    } finally {
      setUpdating(false);
    }
  };

  const exportPdf = () => {
    window.open(
      `${api.defaults.baseURL}/purchase-request/${pr.id}/pdf`,
      '_blank',
      'noopener'
    );
  };

  // Helper note above the action row — explains what the user can do.
  let note = null;
  if (canSendForApproval) {
    note = {
      title: 'You are the request author',
      body: 'You can send for approval when you are ready. Once finance approves it, you can send the PR PDF to the supplier.',
    };
  } else if (canApproveOrReject) {
    note = {
      title: 'Awaiting your approval',
      body: 'You are a finance reviewer. Approve to allow the requester to send the PR to the supplier. Rejecting returns the request to the author.',
    };
  }

  return (
    <Modal
      opened={!!pr}
      onClose={onClose}
      size="lg"
      centered
      title={
        <Group gap="sm">
          <Text fw={600} size="lg">
            {pr.request_code}
          </Text>
          <StatusBadge status={status} />
        </Group>
      }
    >
      <Stack gap="md">
        <FieldRow label="Request name" value={pr.request_name} />
        <SimpleGrid cols={2} spacing="md">
          <FieldRow label="Author" value={pr.request_author} />
          <FieldRow label="Code" value={pr.request_code} />
          <FieldRow label="Supplier" value={pr.supplier_name} />
          <FieldRow label="Supplier email" value={pr.supplier_email} />
        </SimpleGrid>
        <FieldRow label="Request details" value={pr.request_details} />

        <Divider />

        <Stack gap="sm">
          <Text fw={600}>Related invoices</Text>

          {invoiceStatus === 'loading' && (
            <Group gap="xs">
              <Loader size="xs" />
              <Text size="sm" c="dimmed">
                Loading invoices...
              </Text>
            </Group>
          )}

          {invoiceStatus === 'success' &&
            invoiceState.items.length === 0 && (
              <Text size="sm" c="dimmed">
                No related invoices
              </Text>
            )}

          {invoiceStatus === 'error' && (
            <Text size="sm" c="red">
              {invoiceState.message}
            </Text>
          )}

          {invoiceStatus === 'success' &&
            invoiceState.items.map((invoice) => (
              <Card key={invoice.id} withBorder padding="sm" radius="md">
                <Group justify="space-between" align="flex-start" wrap="nowrap">
                  <Stack gap={2}>
                    <Text size="xs" fw={500} c="dimmed">
                      Invoice number
                    </Text>
                    <Text size="sm" fw={500}>
                      {invoice.invoice_number}
                    </Text>
                  </Stack>
                  <Badge color="gray" variant="light" radius="xl">
                    {invoice.invoice_status}
                  </Badge>
                </Group>
                <SimpleGrid cols={2} spacing="sm" mt="sm">
                  <FieldRow
                    label="Invoice sum"
                    value={formatAmount(invoice.invoice_sum)}
                  />
                  <FieldRow
                    label="Paid sum"
                    value={formatAmount(invoice.invoice_sum_paid)}
                  />
                </SimpleGrid>
              </Card>
            ))}
        </Stack>

        {note && (
          <Stack bg="gray.0" p="sm" gap={2} style={{ borderRadius: 4 }}>
            <Text size="xs" fw={600} c="dimmed">
              {note.title}
            </Text>
            <Text size="xs" c="dimmed">
              {note.body}
            </Text>
          </Stack>
        )}

        {error && (
          <Text c="red" size="sm">
            {error}
          </Text>
        )}

        <Divider />

        <Group justify="space-between">
          <Button variant="default" onClick={exportPdf}>
            PDF
          </Button>
          <Group gap="sm">
            {canSendForApproval && (
              <Button
                color="teal"
                onClick={() => transition('sent for approval')}
                loading={updating}
              >
                Send for approval
              </Button>
            )}
            {canApproveOrReject && (
              <>
                <Button
                  color="red"
                  variant="outline"
                  onClick={() => transition('rejected')}
                  loading={updating}
                >
                  Reject
                </Button>
                <Button
                  color="teal"
                  onClick={() => transition('approved')}
                  loading={updating}
                >
                  Approve
                </Button>
              </>
            )}
          </Group>
        </Group>
      </Stack>
    </Modal>
  );
}
