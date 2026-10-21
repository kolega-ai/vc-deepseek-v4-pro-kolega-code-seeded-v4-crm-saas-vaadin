-- Reference data: the plan catalog. Prices live ONLY in this table and are
-- always read server-side; a client never supplies a price or an MRR figure.

insert into plans (code, name, base_monthly_price, per_seat_monthly_price, seats_included, description, features, sort_order, active) values
  ('STARTER', 'Starter', 299.00, 29.00, 5,
   'For small teams getting their customer operations organized.',
   'CRM basics; 5 seats included; email support; 10 GB document storage; 2 API keys', 1, true),
  ('PRO', 'Professional', 999.00, 49.00, 15,
   'For growing B2B teams that run structured renewals and escalations.',
   'Everything in Starter; 15 seats; priority support; 100 GB storage; 10 API keys; webhooks; SLA tracking', 2, true),
  ('ENTERPRISE', 'Enterprise', 3999.00, 79.00, 50,
   'For organizations with dedicated CS operations and compliance needs.',
   'Everything in Professional; 50 seats; unlimited storage; SSO; audit exports; dedicated CSM; custom SLAs', 3, true);
