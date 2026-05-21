import { useEffect, useRef, useState } from 'react';
import { useChatStore } from '@shared/store/chatStore';

export default function ChatWidget() {
  const {
    isOpen,
    messages,
    isStreaming,
    pendingConfirmation,
    suggestions,
    isLoading,
    error,
    toggleChat,
    sendMessage,
    confirmAction,
    rejectAction,
    fetchSuggestions,
    cancelStreaming,
  } = useChatStore();

  const [input, setInput] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const messagesContainerRef = useRef<HTMLDivElement>(null);

  // Fetch suggestions when opened
  useEffect(() => {
    if (isOpen) {
      fetchSuggestions();
    }
  }, [isOpen, fetchSuggestions]);

  // Auto-scroll to bottom when messages or streaming status changes
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isStreaming, pendingConfirmation]);

  const handleSend = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!input.trim() || isStreaming) return;

    const messageText = input.trim();
    setInput('');
    await sendMessage(messageText);
  };

  const handleSuggestionClick = async (text: string) => {
    if (isStreaming) return;
    await sendMessage(text);
  };

  const formatPrice = (price: number) => {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND',
    }).format(price);
  };

  if (!isOpen) {
    return (
      <button
        onClick={toggleChat}
        className="fixed bottom-6 right-6 z-50 flex h-14 w-14 items-center justify-center rounded-full bg-gradient-to-r from-blue-600 to-indigo-600 text-white shadow-lg transition-all duration-300 hover:scale-110 hover:shadow-xl active:scale-95"
        title="Trợ lý AI"
        id="ai-chat-widget-button"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
          strokeWidth={2}
          stroke="currentColor"
          className="h-7 w-7 animate-pulse"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M8.625 12a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0H8.25m4.125 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0H12m4.125 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0h-.375M21 12c0 4.556-4.03 8.25-9 8.25a9.764 9.764 0 01-2.555-.337A5.972 5.972 0 015.41 20.97a.75.75 0 01-1.074-.765 6.002 6.002 0 013.003-4.484C6.042 14.433 5 13.317 5 12c0-4.556 4.03-8.25 9-8.25s9 3.694 9 8.25z"
          />
        </svg>
      </button>
    );
  }

  return (
    <div
      className="fixed bottom-24 right-6 z-50 flex h-[550px] w-[400px] flex-col overflow-hidden rounded-2xl border border-gray-200/50 bg-white/90 shadow-2xl backdrop-blur-md transition-all duration-300 animate-in slide-in-from-bottom-5"
      id="ai-chat-widget-panel"
    >
      {/* Header */}
      <div className="flex items-center justify-between bg-gradient-to-r from-blue-600 to-indigo-600 px-4 py-3 text-white">
        <div className="flex items-center space-x-3">
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-white/20">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              strokeWidth={2.5}
              stroke="currentColor"
              className="h-5 w-5 text-white"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M9 17.25v1.007a3 3 0 01-.879 2.122L7.5 21h9l-.621-.621A3 3 0 0115 18.257V17.25m6-12V15a2.25 2.25 0 01-2.25 2.25H5.25A2.25 2.25 0 013 15V5.25m18 0A2.25 2.25 0 0018.75 3H5.25A2.25 2.25 0 003 5.25m18 0V12a2.25 2.25 0 01-2.25 2.25H5.25A2.25 2.25 0 013 12V5.25"
              />
            </svg>
          </div>
          <div>
            <h3 className="font-semibold text-sm">Trợ lý AI Paradise</h3>
            <span className="text-[10px] text-blue-100 flex items-center gap-1">
              <span className="h-1.5 w-1.5 rounded-full bg-green-400 animate-ping"></span>
              Sẵn sàng hỗ trợ
            </span>
          </div>
        </div>
        <button
          onClick={toggleChat}
          className="rounded-full p-1 hover:bg-white/10 active:bg-white/20"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
            strokeWidth={2.5}
            stroke="currentColor"
            className="h-5 w-5"
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      {/* Messages */}
      <div
        ref={messagesContainerRef}
        className="flex-1 overflow-y-auto p-4 space-y-4 bg-gray-50/50"
      >
        {messages.map((msg, index) => {
          const isUser = msg.role === 'USER';
          return (
            <div
              key={index}
              className={`flex flex-col ${isUser ? 'items-end' : 'items-start'} space-y-1`}
            >
              <div
                className={`max-w-[85%] rounded-2xl px-4 py-2.5 text-sm shadow-sm ${
                  isUser
                    ? 'bg-gradient-to-r from-blue-600 to-indigo-600 text-white rounded-tr-none'
                    : 'bg-white text-gray-800 border border-gray-100 rounded-tl-none'
                }`}
              >
                {msg.content}

                {/* Structured Products Output */}
                {msg.products && msg.products.length > 0 && (
                  <div className="mt-3 grid grid-cols-1 gap-2 pt-2 border-t border-gray-100">
                    {msg.products.map((p: any, pIdx: number) => (
                      <a
                        key={pIdx}
                        href={`/products/${p.id || p.productId}`}
                        className="flex items-center space-x-3 rounded-lg border border-gray-100 bg-gray-50 p-2 hover:bg-gray-100 transition-colors"
                      >
                        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded bg-white font-bold text-xs text-blue-600 border border-gray-200">
                          {p.name.charAt(0)}
                        </div>
                        <div className="overflow-hidden">
                          <p className="truncate text-xs font-semibold text-gray-800">{p.name}</p>
                          <p className="text-[11px] font-bold text-blue-600">{formatPrice(p.price)}</p>
                        </div>
                      </a>
                    ))}
                  </div>
                )}
              </div>
              <span className="text-[10px] text-gray-400 px-1">
                {msg.createdAt ? new Date(msg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
              </span>
            </div>
          );
        })}

        {/* Level 3 Confirmation Card */}
        {pendingConfirmation && (
          <div className="rounded-xl border border-amber-200 bg-amber-50/70 p-4 shadow-sm animate-in fade-in zoom-in-95 duration-200">
            <div className="flex items-start space-x-2">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
                strokeWidth={2}
                stroke="currentColor"
                className="h-5 w-5 text-amber-600 shrink-0 mt-0.5"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
                />
              </svg>
              <div>
                <h4 className="font-bold text-xs text-amber-800">Yêu cầu xác nhận giao dịch</h4>
                <p className="text-xs text-amber-700 mt-1 leading-relaxed">{pendingConfirmation.summary}</p>
                {pendingConfirmation.orderId && (
                  <p className="text-[11px] font-semibold text-amber-600 mt-0.5">Mã đơn hàng: #{pendingConfirmation.orderId}</p>
                )}
              </div>
            </div>
            <div className="mt-3 flex justify-end space-x-2 border-t border-amber-100 pt-3">
              <button
                onClick={() => rejectAction(pendingConfirmation.confirmId)}
                className="rounded-lg bg-white px-3 py-1.5 text-xs font-semibold text-amber-800 border border-amber-200 hover:bg-amber-100 transition-colors"
              >
                Từ chối
              </button>
              <button
                onClick={() => confirmAction(pendingConfirmation.confirmId)}
                className="rounded-lg bg-gradient-to-r from-blue-600 to-indigo-600 px-3 py-1.5 text-xs font-semibold text-white shadow hover:from-blue-700 hover:to-indigo-700 transition-colors"
              >
                Đồng ý xác nhận
              </button>
            </div>
          </div>
        )}

        {/* Loading / Streaming typing indicator */}
        {isStreaming && !pendingConfirmation && (
          <div className="flex items-center space-x-1.5 text-gray-400 bg-white border border-gray-100 rounded-2xl rounded-tl-none px-4 py-2 w-16 shadow-sm">
            <span className="h-1.5 w-1.5 rounded-full bg-gray-400 animate-bounce"></span>
            <span className="h-1.5 w-1.5 rounded-full bg-gray-400 animate-bounce delay-150"></span>
            <span className="h-1.5 w-1.5 rounded-full bg-gray-400 animate-bounce delay-300"></span>
          </div>
        )}

        {/* Error message */}
        {error && (
          <div className="rounded-lg bg-red-50 border border-red-100 p-3 text-xs text-red-600 flex items-start gap-2">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              strokeWidth={2}
              stroke="currentColor"
              className="h-4 w-4 shrink-0"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
              />
            </svg>
            <span>{error}</span>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Suggestion Chips */}
      {suggestions.length > 0 && !isStreaming && !pendingConfirmation && (
        <div className="border-t border-gray-100 bg-gray-50 px-3 py-2">
          <div className="flex flex-wrap gap-1.5 max-h-20 overflow-y-auto">
            {suggestions.map((sug, sIdx) => (
              <button
                key={sIdx}
                onClick={() => handleSuggestionClick(typeof sug === 'string' ? sug : (sug as any).text)}
                className="rounded-full bg-white px-2.5 py-1 text-left text-xs text-gray-600 border border-gray-200/60 hover:bg-blue-50 hover:text-blue-600 hover:border-blue-200 transition-colors duration-200 truncate max-w-full"
              >
                {typeof sug === 'string' ? sug : `${(sug as any).icon || '💡'} ${(sug as any).text}`}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Footer input form */}
      <form onSubmit={handleSend} className="border-t border-gray-100 bg-white p-3 flex items-center space-x-2">
        {isStreaming ? (
          <button
            type="button"
            onClick={cancelStreaming}
            className="flex-1 flex justify-center items-center gap-1.5 rounded-lg border border-red-200 bg-red-50 py-2.5 text-xs font-semibold text-red-600 hover:bg-red-100 active:scale-[0.98] transition-all duration-200"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              strokeWidth={2}
              stroke="currentColor"
              className="h-4 w-4"
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M9.75 9.75l4.5 4.5m0-4.5l-4.5 4.5M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            Dừng AI trả lời
          </button>
        ) : (
          <>
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              disabled={isStreaming || !!pendingConfirmation}
              placeholder={pendingConfirmation ? 'Vui lòng phản hồi xác nhận ở trên...' : 'Hỏi trợ lý AI...'}
              className="flex-1 rounded-lg border border-gray-200 px-3.5 py-2 text-sm focus:border-blue-500 focus:outline-none disabled:bg-gray-100 disabled:text-gray-400"
              id="ai-chat-input"
            />
            <button
              type="submit"
              disabled={!input.trim() || isStreaming || !!pendingConfirmation}
              className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-gradient-to-r from-blue-600 to-indigo-600 text-white shadow transition-all duration-200 hover:from-blue-700 hover:to-indigo-700 disabled:from-gray-300 disabled:to-gray-300 disabled:shadow-none"
              id="ai-chat-send"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
                strokeWidth={2.5}
                stroke="currentColor"
                className="h-4 w-4 transform rotate-90"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M6 12L3.269 3.126A59.768 59.768 0 0121.485 12 59.77 59.77 0 013.27 20.876L5.999 12zm0 0h7.5"
                />
              </svg>
            </button>
          </>
        )}
      </form>
    </div>
  );
}
