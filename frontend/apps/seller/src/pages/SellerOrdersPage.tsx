export default function SellerOrdersPage() {
  return (
    <div className="container mx-auto py-8">
      <h1 className="text-3xl font-bold mb-6">Đơn Hàng</h1>
      <div className="bg-white rounded-lg shadow">
        <table className="w-full">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-sm font-medium text-gray-700">Mã Đơn Hàng</th>
              <th className="px-6 py-3 text-left text-sm font-medium text-gray-700">Khách Hàng</th>
              <th className="px-6 py-3 text-left text-sm font-medium text-gray-700">Tổng Tiền</th>
              <th className="px-6 py-3 text-left text-sm font-medium text-gray-700">Trạng Thái</th>
              <th className="px-6 py-3 text-left text-sm font-medium text-gray-700">Ngày Đặt</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td colSpan={5} className="px-6 py-4 text-center text-gray-500">
                Chưa có đơn hàng nào
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}

