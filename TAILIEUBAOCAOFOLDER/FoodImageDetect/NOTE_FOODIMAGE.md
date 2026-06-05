# LƯU Ý: FOODIMAGEDETECT

## Tính năng này KHÔNG TỒN TẠI trong project hiện tại

Qua quá trình đọc toàn bộ codebase và TaiLieu, **không có tính năng FoodImageDetect**
được implement. Dưới đây là các tính năng AI thực tế trong project:

| Tính năng | Folder | Trạng thái |
|---|---|---|
| Face Anti-Spoofing | TAILIEUBAOCAOFOLDER/AntiSpoofing/ | ✅ Đầy đủ |
| Chatbot RAG | TAILIEUBAOCAOFOLDER/Chatbot/ | ✅ Đầy đủ |
| Hybrid Search | TAILIEUBAOCAOFOLDER/HybridSearch/ | ✅ Đầy đủ |
| Recommend by Behavior | TAILIEUBAOCAOFOLDER/RecommendByBehavior/ | ✅ Đầy đủ |
| FoodImageDetect | (folder này) | ❌ Chưa implement |

## Có thể mày nhầm với:

1. **Face Authentication** — nhận diện khuôn mặt để đăng nhập
   → File: `TAILIEUBAOCAOFOLDER/AntiSpoofing/BAOCAO_ANTISPOOFING.md`

2. **Product Image Upload** — seller upload ảnh sản phẩm
   → Đây là tính năng upload thông thường, không có AI

3. **Chatbot** — gợi ý món ăn từ query tự nhiên
   → File: `TAILIEUBAOCAOFOLDER/Chatbot/BAOCAO_CHATBOT.md`

## Nếu thầy/cô hỏi về FoodImageDetect:

Có thể trả lời:
> "Tính năng nhận diện hình ảnh món ăn là hướng phát triển tiếp theo của hệ thống.
> Hiện tại, app sử dụng text-based search (Hybrid BM25 + PhoBERT semantic) để tìm kiếm
> món ăn thay vì image-based search. Image recognition sẽ được tích hợp trong phiên bản
> tiếp theo sử dụng CLIP model để search by image."
