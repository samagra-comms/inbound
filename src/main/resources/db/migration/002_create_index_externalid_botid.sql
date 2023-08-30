CREATE UNIQUE INDEX IF NOT EXISTS delivery_report_external_id_message_state_idx ON public.delivery_report (external_id,bot_id,message_state);
