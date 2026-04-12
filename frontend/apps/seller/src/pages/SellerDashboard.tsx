export default function SellerDashboard() {
  return (
    <div className="container mx-auto py-8">
      <h1 className="text-3xl font-bold mb-6">Dashboard Người Bán</h1>
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
        <div className="bg-blue-50 p-6 rounded-lg">
          <h3 className="text-sm font-medium text-gray-600">Tổng Sản Phẩm</h3>
          <p className="text-2xl font-bold text-blue-600 mt-2">0</p>
        </div>
        <div className="bg-green-50 p-6 rounded-lg">
          <h3 className="text-sm font-medium text-gray-600">Đơn Hàng Hôm Nay</h3>
          <p className="text-2xl font-bold text-green-600 mt-2">0</p>
        </div>
        <div className="bg-purple-50 p-6 rounded-lg">
          <h3 className="text-sm font-medium text-gray-600">Doanh Thu Hôm Nay</h3>
          <p className="text-2xl font-bold text-purple-600 mt-2">₫0</p>
        </div>
        <div className="bg-orange-50 p-6 rounded-lg">
          <h3 className="text-sm font-medium text-gray-600">Điểm Tin Cậy</h3>
          <p className="text-2xl font-bold text-orange-600 mt-2">100%</p>
        </div>
      </div>
      <p className="text-gray-500">Bắt đầu bán sản phẩm để xem thông tin chi tiết</p>
    </div>
  );
}

