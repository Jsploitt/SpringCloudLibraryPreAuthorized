resource "aws_sqs_queue" "user_name_changes" {
  name                       = "${local.name_prefix}-user-name-changes"
  visibility_timeout_seconds = 30
  message_retention_seconds  = 86400 # 1 day
  receive_wait_time_seconds  = 20    # enable long-polling

  tags = merge(local.common_tags, { Name = "${local.name_prefix}-user-name-changes" })
}
