// Frontend full-flow seed dataset for MongoDB.
// Collections:
// - fs_notification.mg_notifications
// - fs_chat.chat_sessions
// - fs_chat.chat_messages
// - fs_chat.pending_confirmations
// - fs_chat.tool_call_logs

const now = new Date();
const ago = (ms) => new Date(now.getTime() - ms);
const later = (ms) => new Date(now.getTime() + ms);

function upsertMany(collection, docs) {
  docs.forEach((doc) => {
    collection.updateOne(
      { _id: doc._id },
      { $set: doc },
      { upsert: true }
    );
  });
}

const notiDb = db.getSiblingDB('fs_notification');
notiDb.createCollection('mg_notifications');
notiDb.mg_notifications.createIndex({ user_id: 1, created_at: -1 });
notiDb.mg_notifications.createIndex({ user_id: 1, is_read: 1 });
notiDb.mg_notifications.createIndex({ created_at: 1 }, { expireAfterSeconds: 7776000 });

upsertMany(notiDb.mg_notifications, [
  {
    _id: 'fe-notif-buyer-order-created',
    user_id: NumberLong(900001),
    type: 'ORDER_CREATED',
    title: 'FE order created',
    body: 'Order FE-ORD-PENDING-900101 is waiting for payment.',
    metadata: '{"order_id":900101,"deeplink":"/orders/900101"}',
    is_read: false,
    priority: 'NORMAL',
    read_at: null,
    created_at: ago(60 * 60 * 1000)
  },
  {
    _id: 'fe-notif-buyer-paid',
    user_id: NumberLong(900001),
    type: 'ORDER_PAID',
    title: 'FE payment successful',
    body: 'Order FE-ORD-PAID-900102 was paid successfully.',
    metadata: '{"order_id":900102,"amount":23990000}',
    is_read: true,
    priority: 'NORMAL',
    read_at: ago(50 * 60 * 1000),
    created_at: ago(55 * 60 * 1000)
  },
  {
    _id: 'fe-notif-buyer-shipping',
    user_id: NumberLong(900001),
    type: 'ORDER_SHIPPED',
    title: 'FE order is shipping',
    body: 'Order FE-ORD-SHIPPING-900103 is on the way with FE-GHN-900103.',
    metadata: '{"order_id":900103,"tracking":"FE-GHN-900103"}',
    is_read: false,
    priority: 'HIGH',
    read_at: null,
    created_at: ago(45 * 60 * 1000)
  },
  {
    _id: 'fe-notif-buyer-delivered',
    user_id: NumberLong(900001),
    type: 'ORDER_DELIVERED',
    title: 'FE order delivered',
    body: 'Order FE-ORD-DELIVERED-900104 was delivered. Refund window is open.',
    metadata: '{"order_id":900104}',
    is_read: false,
    priority: 'HIGH',
    read_at: null,
    created_at: ago(40 * 60 * 1000)
  },
  {
    _id: 'fe-notif-buyer-refund-requested',
    user_id: NumberLong(900001),
    type: 'REFUND_REQUESTED',
    title: 'FE refund pending',
    body: 'Refund #900201 is waiting for admin review.',
    metadata: '{"refund_id":900201}',
    is_read: false,
    priority: 'NORMAL',
    read_at: null,
    created_at: ago(35 * 60 * 1000)
  },
  {
    _id: 'fe-notif-buyer-refund-approved',
    user_id: NumberLong(900001),
    type: 'REFUND_APPROVED',
    title: 'FE refund completed',
    body: 'Refund #900202 was completed successfully.',
    metadata: '{"refund_id":900202,"amount":4990000}',
    is_read: true,
    priority: 'HIGH',
    read_at: ago(28 * 60 * 1000),
    created_at: ago(30 * 60 * 1000)
  },
  {
    _id: 'fe-notif-buyer-refund-rejected',
    user_id: NumberLong(900001),
    type: 'REFUND_REJECTED',
    title: 'FE refund rejected',
    body: 'Refund #900203 was rejected because evidence was not sufficient.',
    metadata: '{"refund_id":900203}',
    is_read: false,
    priority: 'HIGH',
    read_at: null,
    created_at: ago(25 * 60 * 1000)
  },
  {
    _id: 'fe-notif-buyer-payment-failed',
    user_id: NumberLong(900001),
    type: 'PAYMENT_FAILED',
    title: 'FE payment failed',
    body: 'Order FE-ORD-CANCELLED-900105 payment was cancelled.',
    metadata: '{"order_id":900105}',
    is_read: false,
    priority: 'URGENT',
    read_at: null,
    created_at: ago(20 * 60 * 1000)
  },
  {
    _id: 'fe-notif-buyer-flash-starting',
    user_id: NumberLong(900001),
    type: 'FLASH_SALE_STARTING',
    title: 'FE flash sale is live',
    body: 'FE Live Flash Sale is running now with AirPods and Phone deals.',
    metadata: '{"session_id":900001,"deeplink":"/flash-sales"}',
    is_read: false,
    priority: 'URGENT',
    read_at: null,
    created_at: ago(15 * 60 * 1000)
  },
  {
    _id: 'fe-notif-seller-product-approved',
    user_id: NumberLong(900002),
    type: 'PRODUCT_APPROVED',
    title: 'FE product approved',
    body: 'FE Phone Pro Camera Kit is active in the catalog.',
    metadata: '{"product_id":"90000000-0000-4000-8001-000000000101"}',
    is_read: true,
    priority: 'NORMAL',
    read_at: ago(10 * 60 * 1000),
    created_at: ago(12 * 60 * 1000)
  },
  {
    _id: 'fe-notif-seller-product-rejected',
    user_id: NumberLong(900002),
    type: 'PRODUCT_REJECTED',
    title: 'FE product rejected',
    body: 'FE Rejected Sample Bag needs better images and warranty details.',
    metadata: '{"product_id":"90000000-0000-4000-8001-000000000106"}',
    is_read: false,
    priority: 'HIGH',
    read_at: null,
    created_at: ago(9 * 60 * 1000)
  },
  {
    _id: 'fe-notif-seller-transfer-eligible',
    user_id: NumberLong(900002),
    type: 'TRANSFER_ELIGIBLE',
    title: 'FE payout ready',
    body: 'Transfer #900106 is ready for payout.',
    metadata: '{"transfer_id":900106}',
    is_read: false,
    priority: 'NORMAL',
    read_at: null,
    created_at: ago(8 * 60 * 1000)
  },
  {
    _id: 'fe-notif-seller-transfer-paid',
    user_id: NumberLong(900002),
    type: 'TRANSFER_PAID_OUT',
    title: 'FE payout paid out',
    body: 'Transfer #900109 was paid out to Stripe.',
    metadata: '{"transfer_id":900109}',
    is_read: false,
    priority: 'NORMAL',
    read_at: null,
    created_at: ago(7 * 60 * 1000)
  },
  {
    _id: 'fe-notif-admin-refund',
    user_id: NumberLong(900003),
    type: 'REFUND_REQUESTED',
    title: 'FE refund needs review',
    body: 'Refund #900201 is pending admin review.',
    metadata: '{"refund_id":900201,"deeplink":"/refunds"}',
    is_read: false,
    priority: 'HIGH',
    read_at: null,
    created_at: ago(6 * 60 * 1000)
  },
  {
    _id: 'fe-notif-admin-product-pending',
    user_id: NumberLong(900003),
    type: 'PRODUCT_PENDING_REVIEW',
    title: 'FE product pending review',
    body: 'FE Pending Review Backpack is waiting for moderation.',
    metadata: '{"product_id":"90000000-0000-4000-8001-000000000105","deeplink":"/product-moderation"}',
    is_read: false,
    priority: 'NORMAL',
    read_at: null,
    created_at: ago(5 * 60 * 1000)
  }
]);

const chatDb = db.getSiblingDB('fs_chat');
chatDb.createCollection('chat_sessions');
chatDb.createCollection('chat_messages');
chatDb.createCollection('pending_confirmations');
chatDb.createCollection('tool_call_logs');
chatDb.chat_sessions.createIndex({ userId: 1, status: 1 });
chatDb.chat_messages.createIndex({ sessionId: 1, sequenceNo: 1 }, { unique: true });
chatDb.pending_confirmations.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 300 });
chatDb.tool_call_logs.createIndex({ userId: 1, createdAt: -1 });

upsertMany(chatDb.chat_sessions, [
  {
    _id: 'fe-chat-session-active',
    userId: NumberLong(900001),
    status: 'ACTIVE',
    contextSummary: 'Buyer compares FE phone and laptop products.',
    createdAt: ago(2 * 60 * 60 * 1000),
    updatedAt: ago(4 * 60 * 1000),
    closedAt: null
  },
  {
    _id: 'fe-chat-session-confirm',
    userId: NumberLong(900001),
    status: 'ACTIVE',
    contextSummary: 'Buyer asked AI to add FE Phone Pro Camera Kit to cart; confirmation is pending.',
    createdAt: ago(20 * 60 * 1000),
    updatedAt: ago(2 * 60 * 1000),
    closedAt: null
  },
  {
    _id: 'fe-chat-session-closed',
    userId: NumberLong(900001),
    status: 'CLOSED',
    contextSummary: 'Buyer asked about refund status and closed the chat.',
    createdAt: ago(2 * 24 * 60 * 60 * 1000),
    updatedAt: ago(2 * 24 * 60 * 60 * 1000 - 15 * 60 * 1000),
    closedAt: ago(2 * 24 * 60 * 60 * 1000 - 15 * 60 * 1000)
  }
]);

upsertMany(chatDb.chat_messages, [
  {
    _id: 'fe-chat-msg-active-1',
    sessionId: 'fe-chat-session-active',
    role: 'USER',
    content: 'Find me a good phone around 25 million.',
    toolName: null,
    sequenceNo: 1,
    tokensUsed: null,
    createdAt: ago(2 * 60 * 60 * 1000)
  },
  {
    _id: 'fe-chat-msg-active-2',
    sessionId: 'fe-chat-session-active',
    role: 'ASSISTANT',
    content: 'FE Phone Pro Camera Kit is active, in stock, and has a live flash-sale alternative.',
    toolName: null,
    sequenceNo: 2,
    tokensUsed: 64,
    createdAt: ago(2 * 60 * 60 * 1000 - 30 * 1000)
  },
  {
    _id: 'fe-chat-msg-confirm-1',
    sessionId: 'fe-chat-session-confirm',
    role: 'USER',
    content: 'Add the FE Phone Pro Camera Kit to my cart.',
    toolName: null,
    sequenceNo: 1,
    tokensUsed: null,
    createdAt: ago(20 * 60 * 1000)
  },
  {
    _id: 'fe-chat-msg-confirm-2',
    sessionId: 'fe-chat-session-confirm',
    role: 'ASSISTANT',
    content: 'Please confirm adding 1 FE Phone Pro Camera Kit to your cart.',
    toolName: null,
    sequenceNo: 2,
    tokensUsed: 58,
    createdAt: ago(19 * 60 * 1000)
  },
  {
    _id: 'fe-chat-msg-closed-1',
    sessionId: 'fe-chat-session-closed',
    role: 'USER',
    content: 'What is the status of refund 900202?',
    toolName: null,
    sequenceNo: 1,
    tokensUsed: null,
    createdAt: ago(2 * 24 * 60 * 60 * 1000)
  },
  {
    _id: 'fe-chat-msg-closed-2',
    sessionId: 'fe-chat-session-closed',
    role: 'TOOL_CALL',
    content: '{"refundId":900202}',
    toolName: 'get_refund_status',
    sequenceNo: 2,
    tokensUsed: null,
    createdAt: ago(2 * 24 * 60 * 60 * 1000 - 5 * 1000)
  },
  {
    _id: 'fe-chat-msg-closed-3',
    sessionId: 'fe-chat-session-closed',
    role: 'TOOL_RESULT',
    content: '{"status":"COMPLETED","amount":4990000}',
    toolName: 'get_refund_status',
    sequenceNo: 3,
    tokensUsed: null,
    createdAt: ago(2 * 24 * 60 * 60 * 1000 - 6 * 1000)
  },
  {
    _id: 'fe-chat-msg-closed-4',
    sessionId: 'fe-chat-session-closed',
    role: 'ASSISTANT',
    content: 'Refund #900202 is completed for 4,990,000 VND.',
    toolName: null,
    sequenceNo: 4,
    tokensUsed: 42,
    createdAt: ago(2 * 24 * 60 * 60 * 1000 - 7 * 1000)
  }
]);

upsertMany(chatDb.pending_confirmations, [
  {
    _id: 'fe-confirm-add-cart',
    sessionId: 'fe-chat-session-confirm',
    userId: NumberLong(900001),
    toolName: 'add_to_cart',
    toolArguments: '{"variantId":"90000000-0000-4000-9001-000000000101","quantity":1}',
    summary: 'Add 1 FE Phone Pro Camera Kit to cart.',
    status: 'PENDING',
    expiresAt: later(5 * 60 * 1000),
    confirmedAt: null,
    createdAt: ago(2 * 60 * 1000),
    updatedAt: ago(2 * 60 * 1000)
  }
]);

upsertMany(chatDb.tool_call_logs, [
  {
    _id: 'fe-tool-log-refund-status',
    sessionId: 'fe-chat-session-closed',
    messageId: 'fe-chat-msg-closed-2',
    userId: NumberLong(900001),
    toolName: 'get_refund_status',
    arguments: '{"refundId":900202}',
    result: '{"status":"COMPLETED","amount":4990000}',
    status: 'SUCCESS',
    errorCode: null,
    errorMessage: null,
    latencyMs: 84,
    createdAt: ago(2 * 24 * 60 * 60 * 1000 - 6 * 1000)
  },
  {
    _id: 'fe-tool-log-add-cart-blocked',
    sessionId: 'fe-chat-session-confirm',
    messageId: 'fe-chat-msg-confirm-2',
    userId: NumberLong(900001),
    toolName: 'add_to_cart',
    arguments: '{"variantId":"90000000-0000-4000-9001-000000000101","quantity":1}',
    result: null,
    status: 'BLOCKED',
    errorCode: 'CONFIRMATION_REQUIRED',
    errorMessage: 'Sensitive action requires buyer confirmation.',
    latencyMs: 12,
    createdAt: ago(2 * 60 * 1000)
  }
]);

print('Frontend Mongo seed complete: notifications, chat sessions, messages, confirmations, and tool logs.');
