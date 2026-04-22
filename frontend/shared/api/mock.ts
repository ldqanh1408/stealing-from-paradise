import type { AxiosInstance, InternalAxiosRequestConfig } from 'axios';

// ─── Mock API implementations ────────────────────────────────────────────────

const sleep = (ms: number) => new Promise(r => setTimeout(r, ms));

// ─── Mock data ─────────────────────────────────────────────────────────────

const MOCK_ADDRESSES = [
  {
    address_id: 1,
    province_id: 1,
    district_id: 1,
    full_address: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
    is_default: true,
  },
  {
    address_id: 2,
    province_id: 2,
    district_id: 5,
    full_address: '45 Đường Lê Lợi, Quận Hải Châu, TP. Đà Nẵng',
    is_default: false,
  },
];

const MOCK_CART = {
  sellers: [
    {
      seller_id: 1,
      seller_name: 'Shop Sony',
      seller_trust_score: 4.8,
      items: [
        {
          cart_item_id: 1,
          sku_code: 'SONY-WH-1000XM5-BK',
          product_id: '1',
          product_name: 'Tai nghe Sony WH-1000XM5',
          variant_name: 'Đen / Chính hãng',
          unit_price: 6_490_000,
          quantity: 1,
          stock_available: 15,
          is_flash: false,
          subtotal: 6_490_000,
        },
        {
          cart_item_id: 2,
          sku_code: 'KEY-K2-WHITE',
          product_id: '2',
          product_name: 'Bàn phím cơ Keychron K2',
          variant_name: 'Trắng / Hot-swap',
          unit_price: 2_190_000,
          quantity: 2,
          stock_available: 8,
          is_flash: false,
          subtotal: 4_380_000,
        },
      ],
      seller_subtotal: 10_870_000,
    },
    {
      seller_id: 2,
      seller_name: 'Uniqlo Vietnam',
      seller_trust_score: 4.5,
      items: [
        {
          cart_item_id: 3,
          sku_code: 'UQ-TSHIRT-M',
          product_id: '3',
          product_name: 'Áo thun Uniqlo DRY-EX',
          variant_name: 'Xanh dương / Size M',
          unit_price: 299_000,
          quantity: 1,
          stock_available: 20,
          is_flash: false,
          subtotal: 299_000,
        },
      ],
      seller_subtotal: 299_000,
    },
  ],
  total_items: 4,
  subtotal: 11_169_000,
};

const MOCK_PRODUCTS = [
  {
    product_id: '1',
    seller_id: 1,
    seller_name: 'Shop Sony',
    product_name: 'Tai nghe Sony WH-1000XM5',
    description: 'Tai nghe chống ồn tốt nhất thế giới, pin 30h.',
    price: 6_490_000,
    original_price: 7_990_000,
    category: 'Thiết bị âm thanh',
    variants: [
      { sku_code: 'SONY-WH-1000XM5-BK', variant_name: 'Đen / Chính hãng', stock: 15 },
      { sku_code: 'SONY-WH-1000XM5-WH', variant_name: 'Trắng / Chính hãng', stock: 8 },
    ],
    images: ['https://placehold.co/400x400/1a1a2e/FFF?text=Sony+XM5'],
    stock: 23,
    rating: 4.9,
    reviews_count: 2341,
    created_at: '2024-01-01T00:00:00Z',
  },
  {
    product_id: '2',
    seller_id: 1,
    seller_name: 'Shop Sony',
    product_name: 'Bàn phím cơ Keychron K2',
    description: 'Bàn phím cơ không dây 75%, switch Gateron.',
    price: 2_190_000,
    original_price: 2_490_000,
    category: 'Bàn phím',
    variants: [
      { sku_code: 'KEY-K2-WHITE', variant_name: 'Trắng / Hot-swap', stock: 8 },
      { sku_code: 'KEY-K2-BLACK', variant_name: 'Đen / Hot-swap', stock: 12 },
    ],
    images: ['https://placehold.co/400x400/2d2d44/FFF?text=Keychron+K2'],
    stock: 20,
    rating: 4.7,
    reviews_count: 876,
    created_at: '2024-01-05T00:00:00Z',
  },
  {
    product_id: '3',
    seller_id: 2,
    seller_name: 'Uniqlo Vietnam',
    product_name: 'Áo thun Uniqlo DRY-EX',
    description: 'Áo thun thể thao nam, vải nhanh khô.',
    price: 299_000,
    original_price: 399_000,
    category: 'Thời trang nam',
    variants: [
      { sku_code: 'UQ-TSHIRT-M', variant_name: 'Xanh dương / Size M', stock: 20 },
      { sku_code: 'UQ-TSHIRT-L', variant_name: 'Đỏ / Size L', stock: 15 },
    ],
    images: ['https://placehold.co/400x400/e63946/FFF?text=Uniqlo'],
    stock: 35,
    rating: 4.5,
    reviews_count: 421,
    created_at: '2024-01-10T00:00:00Z',
  },
  {
    product_id: '4',
    seller_id: 3,
    seller_name: 'Apple Store VN',
    product_name: 'AirPods Pro 2',
    description: 'Tai nghe không dây chống ồn chủ động.',
    price: 5_990_000,
    original_price: 6_990_000,
    category: 'Thiết bị âm thanh',
    variants: [
      { sku_code: 'APP-AIRPODS-PRO2', variant_name: 'Trắng / Chính hãng', stock: 30 },
    ],
    images: ['https://placehold.co/400x400/f8f9fa/333?text=AirPods'],
    stock: 30,
    rating: 4.8,
    reviews_count: 3421,
    created_at: '2024-01-15T00:00:00Z',
  },
];

const MOCK_ORDERS = [
  {
    order_id: 1,
    parent_order_id: 1,
    order_code: 'FS-20240115-0001',
    seller_id: 1,
    seller_name: 'Shop Sony',
    buyer_id: 1,
    buyer_name: 'Nguyễn Văn A',
    buyer_username: 'nguyenvana',
    status: 'DELIVERED',
    total_amt: 6_490_000,
    final_amt: 6_490_000,
    item_count: 1,
    is_flash_sale: false,
    created_at: '2024-01-15T10:30:00Z',
    updated_at: '2024-01-17T14:00:00Z',
    shipping_address: {
      full_address: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      province_id: 1,
      district_id: 1,
    },
    tracking_number: 'VT123456789',
    carrier: 'ViettelPost',
    items: [
      {
        order_item_id: 101,
        sku_code: 'SONY-WH-1000XM5-BK',
        product_name: 'Tai nghe Sony WH-1000XM5',
        variant_name: 'Đen / Chính hãng',
        image_snapshot: 'https://placehold.co/80x80/1a1a2e/FFF?text=Sony',
        price_snapshot: 6_490_000,
        quantity: 1,
        refunded_quantity: 0,
      },
    ],
  },
  {
    order_id: 2,
    parent_order_id: 2,
    order_code: 'FS-20240120-0002',
    seller_id: 2,
    seller_name: 'Uniqlo Vietnam',
    buyer_id: 1,
    buyer_name: 'Nguyễn Văn A',
    buyer_username: 'nguyenvana',
    status: 'SHIPPING',
    total_amt: 299_000,
    final_amt: 299_000,
    item_count: 1,
    is_flash_sale: false,
    created_at: '2024-01-20T09:15:00Z',
    updated_at: '2024-01-21T08:00:00Z',
    shipping_address: {
      full_address: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      province_id: 1,
      district_id: 1,
    },
    tracking_number: 'GHN987654321',
    carrier: 'GHN',
    items: [
      {
        order_item_id: 201,
        sku_code: 'UQ-TSHIRT-M',
        product_name: 'Áo thun Uniqlo DRY-EX',
        variant_name: 'Xanh dương / Size M',
        image_snapshot: 'https://placehold.co/80x80/e63946/FFF?text=Uniqlo',
        price_snapshot: 299_000,
        quantity: 1,
        refunded_quantity: 0,
      },
    ],
  },
  {
    order_id: 3,
    parent_order_id: 3,
    order_code: 'FS-20240122-0003',
    seller_id: 1,
    seller_name: 'Shop Sony',
    buyer_id: 1,
    buyer_name: 'Nguyễn Văn A',
    buyer_username: 'nguyenvana',
    status: 'PENDING',
    total_amt: 2_190_000,
    final_amt: 2_190_000,
    is_flash_sale: true,
    item_count: 1,
    created_at: '2024-01-22T16:45:00Z',
    updated_at: '2024-01-22T16:45:00Z',
    shipping_address: {
      full_address: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      province_id: 1,
      district_id: 1,
    },
    items: [
      {
        order_item_id: 301,
        sku_code: 'KEY-K2-WHITE',
        product_name: 'Bàn phím cơ Keychron K2',
        variant_name: 'Trắng / Hot-swap',
        image_snapshot: 'https://placehold.co/80x80/2d2d44/FFF?text=Keychron',
        price_snapshot: 2_190_000,
        quantity: 1,
        refunded_quantity: 0,
      },
    ],
  },
  {
    order_id: 4,
    parent_order_id: 4,
    order_code: 'FS-20240123-0004',
    seller_id: 3,
    seller_name: 'Apple Store VN',
    buyer_id: 1,
    buyer_name: 'Nguyễn Văn A',
    buyer_username: 'nguyenvana',
    status: 'PAID',
    total_amt: 5_990_000,
    final_amt: 5_990_000,
    item_count: 1,
    is_flash_sale: false,
    created_at: '2024-01-23T11:00:00Z',
    updated_at: '2024-01-23T11:05:00Z',
    shipping_address: {
      full_address: '45 Đường Lê Lợi, Quận Hải Châu, TP. Đà Nẵng',
      province_id: 2,
      district_id: 5,
    },
    items: [
      {
        order_item_id: 401,
        sku_code: 'APP-AIRPODS-PRO2',
        product_name: 'AirPods Pro 2',
        variant_name: 'Trắng / Chính hãng',
        image_snapshot: 'https://placehold.co/80x80/f8f9fa/333?text=AirPods',
        price_snapshot: 5_990_000,
        quantity: 1,
        refunded_quantity: 0,
      },
    ],
  },
  {
    order_id: 5,
    parent_order_id: 5,
    order_code: 'FS-20240110-0005',
    seller_id: 1,
    seller_name: 'Shop Sony',
    buyer_id: 1,
    buyer_name: 'Nguyễn Văn A',
    buyer_username: 'nguyenvana',
    status: 'CANCELLED',
    total_amt: 3_000_000,
    final_amt: 3_000_000,
    item_count: 1,
    cancelled_by: 'BUYER',
    cancel_reason: 'Thay đổi ý định',
    created_at: '2024-01-10T08:00:00Z',
    updated_at: '2024-01-10T09:00:00Z',
    shipping_address: {
      full_address: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      province_id: 1,
      district_id: 1,
    },
    items: [],
  },
  {
    order_id: 6,
    parent_order_id: 6,
    order_code: 'FS-20240108-0006',
    seller_id: 2,
    seller_name: 'Uniqlo Vietnam',
    buyer_id: 1,
    buyer_name: 'Nguyễn Văn A',
    buyer_username: 'nguyenvana',
    status: 'PARTIALLY_REFUNDED',
    total_amt: 598_000,
    final_amt: 299_000,
    item_count: 2,
    created_at: '2024-01-08T14:00:00Z',
    updated_at: '2024-01-12T10:00:00Z',
    shipping_address: {
      full_address: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      province_id: 1,
      district_id: 1,
    },
    items: [
      {
        order_item_id: 601,
        sku_code: 'UQ-TSHIRT-M',
        product_name: 'Áo thun Uniqlo DRY-EX',
        variant_name: 'Xanh dương / Size M',
        price_snapshot: 299_000,
        quantity: 2,
        refunded_quantity: 1,
      },
    ],
  },
];

const MOCK_PARENT_ORDERS = [
  {
    parent_order_id: 1,
    order_code: 'PO-20240115-0001',
    status: 'DELIVERED',
    total_amt: 6_490_000,
    final_amt: 6_490_000,
    shipping_address: {
      full_address: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      province_id: 1,
      district_id: 1,
    },
    orders: [MOCK_ORDERS[0]],
    created_at: '2024-01-15T10:30:00Z',
    updated_at: '2024-01-17T14:00:00Z',
  },
  {
    parent_order_id: 2,
    order_code: 'PO-20240120-0002',
    status: 'SHIPPING',
    total_amt: 299_000,
    final_amt: 299_000,
    shipping_address: {
      full_address: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      province_id: 1,
      district_id: 1,
    },
    orders: [MOCK_ORDERS[1]],
    created_at: '2024-01-20T09:15:00Z',
    updated_at: '2024-01-21T08:00:00Z',
  },
  {
    parent_order_id: 3,
    order_code: 'PO-20240122-0003',
    status: 'PENDING',
    total_amt: 2_190_000,
    final_amt: 2_190_000,
    shipping_address: {
      full_address: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      province_id: 1,
      district_id: 1,
    },
    orders: [MOCK_ORDERS[2]],
    created_at: '2024-01-22T16:45:00Z',
    updated_at: '2024-01-22T16:45:00Z',
  },
  {
    parent_order_id: 4,
    order_code: 'PO-20240123-0004',
    status: 'PAID',
    total_amt: 5_990_000,
    final_amt: 5_990_000,
    shipping_address: {
      full_address: '45 Đường Lê Lợi, Quận Hải Châu, TP. Đà Nẵng',
      province_id: 2,
      district_id: 5,
    },
    orders: [MOCK_ORDERS[3]],
    created_at: '2024-01-23T11:00:00Z',
    updated_at: '2024-01-23T11:05:00Z',
  },
  {
    parent_order_id: 5,
    order_code: 'PO-20240110-0005',
    status: 'CANCELLED',
    total_amt: 3_000_000,
    final_amt: 3_000_000,
    shipping_address: {
      full_address: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      province_id: 1,
      district_id: 1,
    },
    orders: [MOCK_ORDERS[4]],
    created_at: '2024-01-10T08:00:00Z',
    updated_at: '2024-01-10T09:00:00Z',
  },
  {
    parent_order_id: 6,
    order_code: 'PO-20240108-0006',
    status: 'PARTIALLY_REFUNDED',
    total_amt: 598_000,
    final_amt: 299_000,
    shipping_address: {
      full_address: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      province_id: 1,
      district_id: 1,
    },
    orders: [MOCK_ORDERS[5]],
    created_at: '2024-01-08T14:00:00Z',
    updated_at: '2024-01-12T10:00:00Z',
  },
];

const MOCK_PAYMENTS = [
  {
    transaction_id: 1,
    parent_order_id: 1,
    amount: 6_490_000,
    method: 'stripe',
    status: 'SUCCESS',
    stripe_pi_id: 'pi_mock_1_aabbcc',
    application_fee: 194_700,
    trans_ref: 'TXN-20240115-001',
    paid_at: '2024-01-15T10:32:00Z',
    remaining_seconds: null,
  },
  {
    transaction_id: 2,
    parent_order_id: 2,
    amount: 299_000,
    method: 'stripe',
    status: 'SUCCESS',
    stripe_pi_id: 'pi_mock_2_ddeeff',
    application_fee: 8_970,
    trans_ref: 'TXN-20240120-002',
    paid_at: '2024-01-20T09:18:00Z',
    remaining_seconds: null,
  },
  {
    transaction_id: 3,
    parent_order_id: 3,
    amount: 2_190_000,
    method: 'stripe',
    status: 'PENDING',
    stripe_pi_id: 'pi_mock_3_112233',
    application_fee: 65_700,
    trans_ref: 'TXN-20240122-003',
    paid_at: null,
    remaining_seconds: 542,
  },
  {
    transaction_id: 4,
    parent_order_id: 4,
    amount: 5_990_000,
    method: 'stripe',
    status: 'SUCCESS',
    stripe_pi_id: 'pi_mock_4_556677',
    application_fee: 179_700,
    trans_ref: 'TXN-20240123-004',
    paid_at: '2024-01-23T11:05:00Z',
    remaining_seconds: null,
  },
  {
    transaction_id: 5,
    parent_order_id: 5,
    amount: 3_000_000,
    method: 'stripe',
    status: 'SUCCESS',
    stripe_pi_id: 'pi_mock_5_889900',
    application_fee: 90_000,
    trans_ref: 'TXN-20240110-005',
    paid_at: '2024-01-10T08:05:00Z',
    remaining_seconds: null,
  },
  {
    transaction_id: 6,
    parent_order_id: 6,
    amount: 598_000,
    method: 'stripe',
    status: 'SUCCESS',
    stripe_pi_id: 'pi_mock_6_aabbdd',
    application_fee: 17_940,
    trans_ref: 'TXN-20240108-006',
    paid_at: '2024-01-08T14:05:00Z',
    remaining_seconds: null,
  },
];

const MOCK_REFUNDS = [
  {
    refund_id: 1,
    order_id: 6,
    group_ref: 'GRP-20240112-001',
    type: 'PARTIAL',
    status: 'SUCCESS',
    amount: 299_000,
    reason: 'Sản phẩm lỗi',
    initiated_by: 'BUYER',
    admin_note: 'Đã duyệt hoàn tiền do lỗi vải.',
    adjust_amount: undefined,
    reviewed_by: 100,
    reviewed_at: '2024-01-12T10:30:00Z',
    stripe_refund_id: 're_mock_1_abc123',
    items: [
      {
        order_item_id: 601,
        quantity: 1,
        refund_amount: 299_000,
        item_reason: 'Vải bị rách',
        status: 'SUCCESS',
      },
    ],
    created_at: '2024-01-11T10:00:00Z',
    updated_at: '2024-01-12T10:30:00Z',
  },
  {
    refund_id: 2,
    order_id: 6,
    group_ref: 'GRP-20240118-002',
    type: 'PARTIAL',
    status: 'PENDING',
    amount: 150_000,
    reason: 'Giao thiếu sản phẩm',
    initiated_by: 'BUYER',
    admin_note: undefined,
    adjust_amount: undefined,
    reviewed_by: undefined,
    reviewed_at: undefined,
    stripe_refund_id: undefined,
    items: [
      {
        order_item_id: 601,
        quantity: 1,
        refund_amount: 150_000,
        item_reason: 'Thiếu 1 sản phẩm',
        status: 'PENDING',
      },
    ],
    created_at: '2024-01-18T14:00:00Z',
    updated_at: '2024-01-18T14:00:00Z',
  },
  {
    refund_id: 3,
    order_id: 5,
    group_ref: 'GRP-20240110-003',
    type: 'FULL',
    status: 'REJECTED',
    amount: 3_000_000,
    reason: 'Đã quá thời hạn hoàn tiền',
    initiated_by: 'BUYER',
    admin_note: undefined,
    reject_reason: 'Đã quá thời hạn hoàn tiền 7 ngày',
    fraud_evidence: false,
    adjust_amount: undefined,
    reviewed_by: 100,
    reviewed_at: '2024-01-11T09:00:00Z',
    stripe_refund_id: undefined,
    items: undefined,
    created_at: '2024-01-10T12:00:00Z',
    updated_at: '2024-01-11T09:00:00Z',
  },
  {
    refund_id: 4,
    order_id: 1,
    group_ref: 'GRP-20240117-004',
    type: 'PARTIAL',
    status: 'PENDING',
    amount: 500_000,
    reason: 'Sản phẩm hư hỏng trong vận chuyển',
    initiated_by: 'BUYER',
    admin_note: undefined,
    adjust_amount: undefined,
    reviewed_by: undefined,
    reviewed_at: undefined,
    stripe_refund_id: undefined,
    items: [
      {
        order_item_id: 101,
        quantity: 1,
        refund_amount: 500_000,
        item_reason: 'Hộp bị móp, sản phẩm trầy',
        status: 'PENDING',
      },
    ],
    created_at: '2024-01-17T16:00:00Z',
    updated_at: '2024-01-17T16:00:00Z',
  },
  {
    refund_id: 5,
    order_id: 4,
    group_ref: 'GRP-20240125-005',
    type: 'FULL',
    status: 'PENDING',
    amount: 5_990_000,
    reason: 'Thay đổi ý định',
    initiated_by: 'BUYER',
    admin_note: undefined,
    adjust_amount: undefined,
    reviewed_by: undefined,
    reviewed_at: undefined,
    stripe_refund_id: undefined,
    items: undefined,
    created_at: '2024-01-25T10:00:00Z',
    updated_at: '2024-01-25T10:00:00Z',
  },
];

const MOCK_SELLER_ORDERS = [
  {
    order_id: 1,
    parent_order_id: 1,
    order_code: 'FS-20240115-0001',
    buyer_id: 10,
    buyer_name: 'Nguyễn Văn A',
    buyer_username: 'nguyenvana',
    status: 'DELIVERED',
    total_amt: 6_490_000,
    final_amt: 6_490_000,
    is_flash_sale: false,
    item_count: 1,
    shipping_address: {
      full_address: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      province_id: 1,
      district_id: 1,
    },
    tracking_number: 'VT123456789',
    carrier: 'ViettelPost',
    created_at: '2024-01-15T10:30:00Z',
    updated_at: '2024-01-17T14:00:00Z',
  },
  {
    order_id: 3,
    parent_order_id: 3,
    order_code: 'FS-20240122-0003',
    buyer_id: 10,
    buyer_name: 'Nguyễn Văn A',
    buyer_username: 'nguyenvana',
    status: 'PENDING',
    total_amt: 2_190_000,
    final_amt: 2_190_000,
    is_flash_sale: true,
    item_count: 1,
    shipping_address: {
      full_address: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      province_id: 1,
      district_id: 1,
    },
    tracking_number: null,
    carrier: null,
    created_at: '2024-01-22T16:45:00Z',
    updated_at: '2024-01-22T16:45:00Z',
  },
  {
    order_id: 7,
    parent_order_id: 7,
    order_code: 'FS-20240124-0007',
    buyer_id: 11,
    buyer_name: 'Trần Thị B',
    buyer_username: 'tranthib',
    status: 'PAID',
    total_amt: 12_980_000,
    final_amt: 12_980_000,
    is_flash_sale: false,
    item_count: 2,
    shipping_address: {
      full_address: '78 Đường Hai Bà Trưng, Quận 3, TP. Hồ Chí Minh',
      province_id: 1,
      district_id: 3,
    },
    tracking_number: null,
    carrier: null,
    created_at: '2024-01-24T08:00:00Z',
    updated_at: '2024-01-24T08:05:00Z',
  },
  {
    order_id: 8,
    parent_order_id: 8,
    order_code: 'FS-20240121-0008',
    buyer_id: 12,
    buyer_name: 'Lê Minh C',
    buyer_username: 'leminhc',
    status: 'SHIPPING',
    total_amt: 3_200_000,
    final_amt: 3_200_000,
    is_flash_sale: false,
    item_count: 1,
    shipping_address: {
      full_address: '15 Đường Cái Khế, Quận Ninh Kiều, TP. Cần Thơ',
      province_id: 3,
      district_id: 10,
    },
    tracking_number: 'GHTK555666777',
    carrier: 'GHTK',
    created_at: '2024-01-21T13:00:00Z',
    updated_at: '2024-01-22T10:00:00Z',
  },
  {
    order_id: 9,
    parent_order_id: 9,
    order_code: 'FS-20240118-0009',
    buyer_id: 13,
    buyer_name: 'Phạm Hoàng D',
    buyer_username: 'phamhoangd',
    status: 'CANCELLED',
    total_amt: 1_500_000,
    final_amt: 1_500_000,
    item_count: 1,
    shipping_address: {
      full_address: '88 Đường Lý Thường Kiệt, Quận 5, TP. Hồ Chí Minh',
      province_id: 1,
      district_id: 5,
    },
    tracking_number: null,
    carrier: null,
    created_at: '2024-01-18T09:00:00Z',
    updated_at: '2024-01-18T10:30:00Z',
  },
  {
    order_id: 10,
    parent_order_id: 10,
    order_code: 'FS-20240105-0010',
    buyer_id: 14,
    buyer_name: 'Võ Đình E',
    buyer_username: 'vodinhe',
    status: 'REFUNDED',
    total_amt: 4_000_000,
    final_amt: 4_000_000,
    item_count: 1,
    shipping_address: {
      full_address: '200 Đường 3/2, Quận 10, TP. Hồ Chí Minh',
      province_id: 1,
      district_id: 10,
    },
    tracking_number: null,
    carrier: null,
    created_at: '2024-01-05T11:00:00Z',
    updated_at: '2024-01-12T15:00:00Z',
  },
];

// ─── Checkout state ──────────────────────────────────────────────────────────

let checkoutOrderData: Record<number, {
  parent_order_id: number;
  order_code: string;
  orders: any[];
  total_amount: number;
  final_amount: number;
  items_count: number;
  timeout_at: string;
  created_at: string;
}> = {};

// ─── Mock handlers ───────────────────────────────────────────────────────────

type MockHandler = (config: InternalAxiosRequestConfig) => Promise<any>;

const mockHandlers: MockHandler[] = [
  // ─── Cart ─────────────────────────────────────────────────────────────────
  async ({ method, url }) => {
    if (url === '/cart' && method === 'get') {
      await sleep(300 + Math.random() * 200);
      return { success: true, data: MOCK_CART, timestamp: Date.now() };
    }
    if (url === '/cart' && method === 'delete') {
      await sleep(200 + Math.random() * 100);
      return { success: true, data: null, timestamp: Date.now() };
    }
    if (url?.startsWith('/cart/items') && method === 'post') {
      await sleep(200 + Math.random() * 100);
      return { success: true, data: { cart_item_id: Date.now(), sku_code: 'MOCK-SKU', product_name: 'Sản phẩm mới', variant_name: 'Mặc định', unit_price: 999_000, quantity: 1, stock_available: 50, is_flash: false }, timestamp: Date.now() };
    }
    if (url?.match(/^\/cart\/items\/\d+$/) && method === 'put') {
      await sleep(200 + Math.random() * 100);
      return { success: true, data: { cart_item_id: parseInt(url!.split('/').pop()!), sku_code: 'MOCK-SKU', product_name: 'Sản phẩm', variant_name: 'Mặc định', unit_price: 999_000, quantity: 1, stock_available: 50, is_flash: false }, timestamp: Date.now() };
    }
    if (url?.match(/^\/cart\/items\/\d+$/) && method === 'delete') {
      await sleep(200 + Math.random() * 100);
      return { success: true, data: null, timestamp: Date.now() };
    }
    return null;
  },

  // ─── Orders ────────────────────────────────────────────────────────────────
  async ({ method, url, params, data }) => {
    if (url === '/orders/checkout' && method === 'post') {
      await sleep(500 + Math.random() * 300);
      const body = JSON.parse(data || '{}');
      const poId = Date.now();
      const now = new Date().toISOString();
      const orderId = poId - 1;
      const orderData = {
        parent_order_id: poId,
        order_code: `PO-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}-${String(poId).slice(-4)}`,
        orders: [
          {
            order_id: orderId,
            order_code: `FS-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}-${String(orderId).slice(-4)}`,
            seller_id: 1,
            seller_name: 'Shop Sony',
            total_amt: 6_490_000,
            final_amt: 6_490_000,
            status: 'PENDING',
            item_count: body.item_ids?.length || 1,
            created_at: now,
          },
        ],
        total_amount: 6_490_000,
        final_amount: 6_490_000,
        items_count: body.item_ids?.length || 1,
        timeout_at: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
        created_at: now,
      };
      checkoutOrderData[poId] = orderData;
      return { success: true, data: orderData, timestamp: Date.now() };
    }

    if (url === '/orders' && method === 'get') {
      await sleep(400 + Math.random() * 200);
      const status = params?.status;
      const page = params?.page ?? 0;
      const size = params?.size ?? 10;
      const filtered = status && status !== 'ALL'
        ? MOCK_ORDERS.filter(o => o.status === status)
        : MOCK_ORDERS;
      const start = page * size;
      const content = filtered.slice(start, start + size);
      return {
        success: true,
        data: {
          content,
          totalElements: filtered.length,
          totalPages: Math.ceil(filtered.length / size),
          last: start + size >= filtered.length,
        },
        timestamp: Date.now(),
      };
    }

    const getOrderByIdMatch = url?.match(/^\/orders\/(\d+)$/);
    if (getOrderByIdMatch && method === 'get') {
      await sleep(300 + Math.random() * 100);
      const orderId = parseInt(getOrderByIdMatch[1]);
      const order = MOCK_ORDERS.find(o => o.order_id === orderId);
      if (!order) throw { response: { status: 404, data: { message: 'Order not found' } } };
      return { success: true, data: order, timestamp: Date.now() };
    }

    const getParentOrderMatch = url?.match(/^\/orders\/parent\/(\d+)$/);
    if (getParentOrderMatch && method === 'get') {
      await sleep(300 + Math.random() * 100);
      const parentId = parseInt(getParentOrderMatch[1]);
      // If there's a pending checkout, return it
      if (checkoutOrderData[parentId]) {
        const cd = checkoutOrderData[parentId];
        return {
          success: true,
          data: {
            parent_order_id: parentId,
            order_code: cd.order_code,
            status: 'PENDING',
            total_amt: cd.total_amount,
            final_amt: cd.final_amount,
            shipping_address: MOCK_ADDRESSES[0],
            orders: cd.orders.map(o => ({
              ...o,
              seller_id: 1,
              seller_name: 'Shop Sony',
              buyer_id: 1,
              buyer_name: 'Nguyễn Văn A',
              shipping_address: MOCK_ADDRESSES[0],
              created_at: cd.created_at,
              items: [{
                order_item_id: 1,
                sku_code: 'KEY-K2-WHITE',
                product_name: 'Bàn phím cơ Keychron K2',
                variant_name: 'Trắng / Hot-swap',
                price_snapshot: 2_190_000,
                quantity: 1,
                refunded_quantity: 0,
              }],
            })),
            created_at: cd.created_at,
            updated_at: cd.created_at,
          },
          timestamp: Date.now(),
        };
      }
      const po = MOCK_PARENT_ORDERS.find(p => p.parent_order_id === parentId);
      if (!po) throw { response: { status: 404, data: { message: 'Parent order not found' } } };
      return { success: true, data: po, timestamp: Date.now() };
    }

    const cancelMatch = url?.match(/^\/orders\/(\d+)\/cancel$/);
    if (cancelMatch && method === 'post') {
      await sleep(400 + Math.random() * 200);
      const orderId = parseInt(cancelMatch[1]);
      return {
        success: true,
        data: { order_id: orderId, order_code: `FS-MOCK-${orderId}`, status: 'CANCELLED', cancelled_by: 'BUYER', cancelled_at: new Date().toISOString() },
        timestamp: Date.now(),
      };
    }

    const trackingMatch = url?.match(/^\/orders\/(\d+)\/tracking$/);
    if (trackingMatch && method === 'put') {
      await sleep(400 + Math.random() * 200);
      const orderId = parseInt(trackingMatch[1]);
      const body = JSON.parse(data || '{}');
      return {
        success: true,
        data: {
          order_id: orderId,
          order_code: `FS-MOCK-${orderId}`,
          status: 'SHIPPING',
          tracking_number: body.tracking_number,
          carrier: body.carrier || 'ViettelPost',
          shipping_deadline: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString(),
        },
        timestamp: Date.now(),
      };
    }

    const confirmMatch = url?.match(/^\/orders\/(\d+)\/confirm-received$/);
    if (confirmMatch && method === 'post') {
      await sleep(400 + Math.random() * 200);
      const orderId = parseInt(confirmMatch[1]);
      return { success: true, data: { order_id: orderId, status: 'DELIVERED' }, timestamp: Date.now() };
    }

    const rtsMatch = url?.match(/^\/orders\/(\d+)\/return-to-sender$/);
    if (rtsMatch && method === 'post') {
      await sleep(500 + Math.random() * 300);
      const orderId = parseInt(rtsMatch[1]);
      return {
        success: true,
        data: {
          order_id: orderId,
          order_code: `FS-MOCK-${orderId}`,
          order_status: 'RETURNED',
          refund_id: Date.now(),
          refund_status: 'PENDING',
          refund_amount: 1_000_000,
        },
        timestamp: Date.now(),
      };
    }

    // ─── Seller Orders ────────────────────────────────────────────────────────
    if (url === '/sellers/me/orders' && method === 'get') {
      await sleep(400 + Math.random() * 200);
      const status = params?.status;
      const page = params?.page ?? 0;
      const size = params?.size ?? 20;
      const filtered = status && status !== 'ALL'
        ? MOCK_SELLER_ORDERS.filter(o => o.status === status)
        : MOCK_SELLER_ORDERS;
      const start = page * size;
      const content = filtered.slice(start, start + size);
      return {
        success: true,
        data: {
          content,
          totalElements: filtered.length,
          totalPages: Math.ceil(filtered.length / size),
          last: start + size >= filtered.length,
        },
        timestamp: Date.now(),
      };
    }

    // ─── Order Refunds (buyer) ────────────────────────────────────────────────
    const orderRefundMatch = url?.match(/^\/orders\/(\d+)\/refunds$/);
    if (orderRefundMatch && method === 'post') {
      await sleep(500 + Math.random() * 300);
      const orderId = parseInt(orderRefundMatch[1]);
      const body = JSON.parse(data || '{}');
      return {
        success: true,
        data: {
          refund_id: Date.now(),
          order_id: orderId,
          group_ref: `GRP-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}-${String(Date.now()).slice(-4)}`,
          type: 'PARTIAL',
          status: 'PENDING',
          amount: body.items?.reduce((s: number, it: any) => s + (it.quantity * 2_190_000), 0) || 299_000,
          reason: body.reason || 'Sản phẩm lỗi',
          initiated_by: 'BUYER',
          items: body.items?.map((it: any) => ({
            order_item_id: it.order_item_id,
            quantity: it.quantity,
            refund_amount: it.quantity * 2_190_000,
            item_reason: it.item_reason,
            status: 'PENDING',
          })),
          created_at: new Date().toISOString(),
        },
        timestamp: Date.now(),
      };
    }

    if (orderRefundMatch && method === 'get') {
      await sleep(300 + Math.random() * 100);
      const orderId = parseInt(orderRefundMatch[1]);
      const orderRefunds = MOCK_REFUNDS.filter(r => r.order_id === orderId);
      return { success: true, data: orderRefunds, timestamp: Date.now() };
    }

    const parentRefundMatch = url?.match(/^\/orders\/parent\/(\d+)\/refund$/);
    if (parentRefundMatch && method === 'post') {
      await sleep(500 + Math.random() * 300);
      const parentId = parseInt(parentRefundMatch[1]);
      return {
        success: true,
        data: {
          group_ref: `GRP-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}-${String(Date.now()).slice(-4)}`,
          type: 'FULL',
          total_amount: 2_190_000,
          status: 'PENDING',
          refunds: [{ refund_id: Date.now(), order_id: parentId * 10, seller_id: 1, amount: 2_190_000, item_count: 1 }],
          loyalty_points_to_return: 219,
          estimated_days: 3,
        },
        timestamp: Date.now(),
      };
    }

    if (parentRefundMatch && method === 'get') {
      await sleep(300 + Math.random() * 100);
      return {
        success: true,
        data: {
          group_ref: `GRP-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}-0001`,
          type: 'FULL',
          overall_status: 'SUCCESS',
          total_amount: 2_190_000,
          refunds: [{ refund_id: 1, order_id: 3, status: 'SUCCESS', refund_ref: 're_mock_full_1' }],
        },
        timestamp: Date.now(),
      };
    }

    if (url === '/orders/refunds' && method === 'get') {
      await sleep(400 + Math.random() * 200);
      const page = params?.page ?? 0;
      const size = params?.size ?? 10;
      const start = page * size;
      const content = MOCK_REFUNDS.slice(start, start + size);
      return {
        success: true,
        data: { content, total_elements: MOCK_REFUNDS.length, total_pages: Math.ceil(MOCK_REFUNDS.length / size) },
        timestamp: Date.now(),
      };
    }

    return null;
  },

  // ─── Payments ──────────────────────────────────────────────────────────────
  async ({ method, url }) => {
    const parentOrderMatch = url?.match(/^\/payments\/parent-order\/(\d+)$/);
    if (parentOrderMatch && method === 'get') {
      await sleep(300 + Math.random() * 100);
      const parentId = parseInt(parentOrderMatch[1]);
      // Return pending checkout data if exists
      if (checkoutOrderData[parentId]) {
        return {
          success: true,
          data: {
            transaction_id: parentId,
            parent_order_id: parentId,
            amount: checkoutOrderData[parentId].final_amount,
            method: 'stripe',
            status: 'PENDING',
            stripe_pi_id: `pi_mock_${parentId}`,
            application_fee: Math.round(checkoutOrderData[parentId].final_amount * 0.03),
            trans_ref: `TXN-MOCK-${parentId}`,
            paid_at: null,
            remaining_seconds: 542,
          },
          timestamp: Date.now(),
        };
      }
      const payment = MOCK_PAYMENTS.find(p => p.parent_order_id === parentId);
      if (!payment) throw { response: { status: 404, data: { message: 'Payment not found' } } };
      return { success: true, data: payment, timestamp: Date.now() };
    }

    const clientSecretMatch = url?.match(/^\/payments\/parent-order\/(\d+)\/client-secret$/);
    if (clientSecretMatch && method === 'get') {
      await sleep(500 + Math.random() * 200);
      const parentId = parseInt(clientSecretMatch[1]);
      return {
        success: true,
        data: {
          parent_order_id: parentId,
          transaction_id: parentId,
          client_secret: `pi_mock_secret_${parentId}_${Date.now()}_test_mock_secret`,
          status: 'requires_payment_method',
        },
        timestamp: Date.now(),
      };
    }

    return null;
  },

  // ─── Addresses ──────────────────────────────────────────────────────────────
  async ({ method, url, data }) => {
    if (url === '/users/me/addresses' && method === 'get') {
      await sleep(300 + Math.random() * 100);
      return { success: true, data: MOCK_ADDRESSES, timestamp: Date.now() };
    }
    if (url === '/users/me/addresses' && method === 'post') {
      await sleep(400 + Math.random() * 200);
      const body = JSON.parse(data || '{}');
      return {
        success: true,
        data: { address_id: Date.now(), ...body, is_default: body.is_default ?? false },
        timestamp: Date.now(),
      };
    }
    const addrUpdateMatch = url?.match(/^\/users\/me\/addresses\/(\d+)$/);
    if (addrUpdateMatch && method === 'put') {
      await sleep(300 + Math.random() * 100);
      const addressId = parseInt(addrUpdateMatch[1]);
      const body = JSON.parse(data || '{}');
      return { success: true, data: { ...MOCK_ADDRESSES[0], address_id: addressId, ...body }, timestamp: Date.now() };
    }
    if (addrUpdateMatch && method === 'delete') {
      await sleep(300 + Math.random() * 100);
      return { success: true, data: null, timestamp: Date.now() };
    }
    return null;
  },

  // ─── Products ───────────────────────────────────────────────────────────────
  async ({ method, url, params }) => {
    if ((url === '/products' || url === '/search') && method === 'get') {
      await sleep(300 + Math.random() * 200);
      const search = params?.q || params?.search || '';
      const category = params?.category;
      let filtered = MOCK_PRODUCTS;
      if (search) filtered = filtered.filter(p => p.product_name.toLowerCase().includes(search.toLowerCase()) || p.description?.toLowerCase().includes(search.toLowerCase()));
      if (category) filtered = filtered.filter(p => p.category === category);
      return {
        success: true,
        data: { content: filtered, totalElements: filtered.length, totalPages: 1, last: true },
        timestamp: Date.now(),
      };
    }
    const productMatch = url?.match(/^\/products\/([^/]+)$/);
    if (productMatch && method === 'get') {
      await sleep(200 + Math.random() * 100);
      const product = MOCK_PRODUCTS.find(p => p.product_id === productMatch[1]);
      if (!product) throw { response: { status: 404, data: { message: 'Product not found' } } };
      return { success: true, data: product, timestamp: Date.now() };
    }
    return null;
  },

  // ─── Auth ───────────────────────────────────────────────────────────────────
  async ({ method, url, data }) => {
    if (url === '/auth/login' && method === 'post') {
      await sleep(500 + Math.random() * 200);
      return {
        success: true,
        data: {
          accessToken: 'mock_access_token_' + Date.now(),
          refreshToken: 'mock_refresh_token_' + Date.now(),
          userId: 1,
          username: 'nguyenvana',
          email: 'nguyenvana@example.com',
          role: 'CUSTOMER',
          expiresIn: 3600,
        },
        timestamp: Date.now(),
      };
    }
    if (url === '/auth/register' && method === 'post') {
      await sleep(600 + Math.random() * 300);
      return {
        success: true,
        data: {
          accessToken: 'mock_access_token_' + Date.now(),
          refreshToken: 'mock_refresh_token_' + Date.now(),
          userId: Date.now(),
          username: JSON.parse(data || '{}').username || 'newuser',
          email: JSON.parse(data || '{}').email || 'new@example.com',
          role: 'CUSTOMER',
          expiresIn: 3600,
        },
        timestamp: Date.now(),
      };
    }
    if (url === '/auth/refresh' && method === 'post') {
      await sleep(200);
      return {
        success: true,
        data: { accessToken: 'mock_access_token_' + Date.now(), expiresIn: 3600 },
        timestamp: Date.now(),
      };
    }
    if (url === '/users/me' && method === 'get') {
      await sleep(200 + Math.random() * 100);
      return {
        success: true,
        data: {
          userId: 1,
          username: 'nguyenvana',
          email: 'nguyenvana@example.com',
          role: 'CUSTOMER',
          name: 'Nguyễn Văn A',
          trust_score: 4.8,
        },
        timestamp: Date.now(),
      };
    }
    return null;
  },

  // ─── Admin Refunds ──────────────────────────────────────────────────────────
  async ({ method, url, params, data }) => {
    if (url === '/admin/refunds' && method === 'get') {
      await sleep(400 + Math.random() * 200);
      const status = params?.status;
      const type = params?.type;
      const page = params?.page ?? 0;
      const size = params?.size ?? 20;
      let filtered = [...MOCK_REFUNDS];
      if (status) filtered = filtered.filter(r => r.status === status);
      if (type) filtered = filtered.filter(r => r.type === type);
      const start = page * size;
      const content = filtered.slice(start, start + size);
      return {
        success: true,
        data: {
          content,
          total_elements: filtered.length,
          total_pages: Math.ceil(filtered.length / size),
          last: start + size >= filtered.length,
        },
        timestamp: Date.now(),
      };
    }

    const adminRefundMatch = url?.match(/^\/admin\/refunds\/(\d+)$/);
    if (adminRefundMatch && method === 'get') {
      await sleep(300 + Math.random() * 100);
      const refundId = parseInt(adminRefundMatch[1]);
      const refund = MOCK_REFUNDS.find(r => r.refund_id === refundId);
      if (!refund) throw { response: { status: 404, data: { message: 'Refund not found' } } };
      return { success: true, data: refund, timestamp: Date.now() };
    }

    const approveMatch = url?.match(/^\/admin\/refunds\/(\d+)\/approve$/);
    if (approveMatch && method === 'post') {
      await sleep(500 + Math.random() * 300);
      const refundId = parseInt(approveMatch[1]);
      const body = JSON.parse(data || '{}');
      return {
        success: true,
        data: {
          refund_id: refundId,
          status: 'SUCCESS',
          amount: 299_000,
          tracking_number: body.tracking_number,
          reviewed_by: 'Admin Mock',
          reviewed_at: new Date().toISOString(),
        },
        timestamp: Date.now(),
      };
    }

    const rejectMatch = url?.match(/^\/admin\/refunds\/(\d+)\/reject$/);
    if (rejectMatch && method === 'post') {
      await sleep(400 + Math.random() * 200);
      const refundId = parseInt(rejectMatch[1]);
      return {
        success: true,
        data: { refund_id: refundId, status: 'REJECTED' },
        timestamp: Date.now(),
      };
    }

    return null;
  },
];

// ─── isMockMode ──────────────────────────────────────────────────────────────

export function isMockMode(): boolean {
  return import.meta.env.VITE_BACKEND_MODE === 'mock' || !import.meta.env.VITE_API_URL;
}

export function isNetworkError(error: unknown): boolean {
  if (error instanceof Error) {
    const msg = error.message.toLowerCase();
    return (
      error.name === 'NetworkError' ||
      msg.includes('network') ||
      msg.includes('failed to fetch') ||
      msg.includes('econnrefused') ||
      msg.includes('err_connection') ||
      msg.includes('net::err') ||
      msg.includes('timeout') ||
      (error as any).code === 'ECONNREFUSED' ||
      (error as any).code === 'ERR_NETWORK' ||
      (error as any).code === 'ETIMEDOUT' ||
      (error as any).code === 'ERR_CANCELED'
    );
  }
  return false;
}

export function shouldUseMock(error: unknown): boolean {
  if (isMockMode()) return true;
  return isNetworkError(error);
}

// ─── Install mock interceptor ─────────────────────────────────────────────────

export function installMockInterceptor(apiClient: AxiosInstance) {
  if (!isMockMode()) return;

  apiClient.interceptors.request.use(async (config) => {
    if (!isMockMode()) return config;

    const normalizedConfig = {
      method: config.method?.toLowerCase() || 'get',
      url: config.url?.replace(config.baseURL || '', '') || '',
      params: config.params,
      data: config.data,
    };

    for (const handler of mockHandlers) {
      try {
        const result = await handler(normalizedConfig as InternalAxiosRequestConfig);
        if (result !== null) {
          // Create a mock response that axios can understand
          const mockResponse = {
            data: result,
            status: 200,
            statusText: 'OK',
            headers: {},
            config,
          };
          // Reject with a special object that the response interceptor can handle
          throw mockResponse;
        }
      } catch (err: any) {
        // If it's our mock response, re-throw it as a handled mock
        if (err?.data?.timestamp) {
          const mockError = new Error('Mock response') as any;
          mockError.response = err;
          mockError.isMockResponse = true;
          mockError.config = config;
          return Promise.reject(mockError);
        }
        // Re-throw unknown errors
        if (!err?.isMockResponse) throw err;
      }
    }

    return config;
  });
}
