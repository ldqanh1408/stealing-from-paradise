export default function AdminDashboard() {
  return (
    <div className="container mx-auto py-8">
      <h1 className="text-3xl font-bold mb-6">Dashboard Admin</h1>
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
        <div className="bg-blue-50 p-6 rounded-lg">
          <h3 className="text-sm font-medium text-gray-600">Tổng Người Dùng</h3>
          <p className="text-2xl font-bold text-blue-600 mt-2">0</p>
        </div>
        <div className="bg-green-50 p-6 rounded-lg">
          <h3 className="text-sm font-medium text-gray-600">Sản Phẩm Chờ Duyệt</h3>
          <p className="text-2xl font-bold text-green-600 mt-2">0</p>
        </div>
        <div className="bg-purple-50 p-6 rounded-lg">
          <h3 className="text-sm font-medium text-gray-600">Đơn Hoàn Tiền</h3>
          <p className="text-2xl font-bold text-purple-600 mt-2">0</p>
        </div>
        <div className="bg-orange-50 p-6 rounded-lg">
          <h3 className="text-sm font-medium text-gray-600">Flash Sale Hoạt Động</h3>
          <p className="text-2xl font-bold text-orange-600 mt-2">0</p>
        </div>
      </div>
      <p className="text-gray-500">Quản lý nền tảng từ đây</p>
    </div>
  );
}

