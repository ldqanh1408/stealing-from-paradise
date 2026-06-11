import { useState } from 'react';
import { sellerApi } from '@shared/api/seller.api';

export default function ImageUploader({ productId, images, onChange }: { productId: string; images: string[]; onChange: (imgs: string[]) => void }) {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');

  const handleUpload = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    setUploading(true);
    setError('');
    try {
      const newImages: string[] = [];
      for (const file of Array.from(files)) {
        if (!file.type.startsWith('image/')) continue;
        const { data: resp } = await sellerApi.getPresignedUrl(productId, file.name, file.type);
        const result = resp.data;
        await fetch(result.presignedUrl, { method: 'PUT', body: file, headers: { 'Content-Type': file.type } });
        newImages.push(result.objectUrl);
      }
      onChange([...images, ...newImages]);
    } catch (e: any) {
      setError('Tải ảnh thất bại. Vui lòng thử lại.');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div>
      <label className="block text-sm font-medium text-gray-700 mb-1.5">Hình ảnh</label>
      <div className="flex flex-wrap gap-2 mb-2">
        {images.map((url, i) => (
          <div key={i} className="relative w-16 h-16 rounded-lg overflow-hidden border border-gray-200 group">
            <img src={url} alt="" className="w-full h-full object-cover" />
            <button
              onClick={() => onChange(images.filter((_, j) => j !== i))}
              className="absolute top-0 right-0 w-5 h-5 bg-red-500 text-white rounded-bl-md text-xs opacity-0 group-hover:opacity-100 transition-opacity"
            >×</button>
          </div>
        ))}
        {uploading && (
          <div className="w-16 h-16 rounded-lg border border-dashed border-gray-300 flex items-center justify-center">
            <div className="w-4 h-4 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
          </div>
        )}
      </div>
      <label className="cursor-pointer inline-block">
        <span className="px-3 py-1.5 border border-gray-300 rounded-lg text-xs text-gray-600 hover:bg-gray-50 transition-colors">
          + Thêm ảnh
        </span>
        <input type="file" multiple accept="image/*" className="hidden" onChange={e => handleUpload(e.target.files)} />
      </label>
      {error && <p className="text-xs text-red-500 mt-1">{error}</p>}
    </div>
  );
}
