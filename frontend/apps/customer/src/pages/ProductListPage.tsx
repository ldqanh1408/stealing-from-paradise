export default function ProductListPage() {
  return (
    <div className="container mx-auto py-8">
      <h1 className="text-3xl font-bold mb-6">Sản phẩm</h1>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* TODO: Load products from API */}
        <div className="border rounded p-4">
          <p className="text-gray-500">Sản phẩm sẽ hiển thị ở đây</p>
        </div>
      </div>
    </div>
  );
}

