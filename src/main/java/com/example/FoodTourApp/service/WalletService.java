package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.WalletDTO.WalletResponseDTO;
import com.example.FoodTourApp.DTO.WalletDTO.WalletTransactionResponseDTO;
import com.example.FoodTourApp.entity.Order;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WalletService {

    /**
     * Lấy thông tin ví của user
     */
    WalletResponseDTO getWalletInfo(User user);

    /**
     * Lấy lịch sử giao dịch - WITH PAGINATION
     */
    Page<WalletTransactionResponseDTO> getTransactionHistory(User user, Pageable pageable);

    /**
     * Xử lý thanh toán ví (app_wallet):
     * - Trừ buyer toàn bộ totalAmount
     * - Cộng vào admin wallet dưới dạng escrow (giữ hộ, chưa phải commission)
     * Seller CHƯA nhận tiền ở bước này.
     */
    void processOrderPayment(Order order);

    /**
     * Giải phóng escrow sau khi đơn hàng giao thành công (app_wallet):
     * - Seller nhận sellerReceivedAmount (88%)
     * - Admin giữ lại platformCommissionAmount (12%) — đã có trong ví từ escrow
     */
    void releaseEscrowToSeller(Order order);

    /**
     * Xử lý hoa hồng đơn COD sau khi giao thành công:
     * - Trừ commission (12%) từ ví seller
     * - Cộng commission vào admin wallet
     * Nếu ví seller không đủ → vẫn thực hiện (debt tracking, số dư âm)
     */
    void processCommissionCOD(Order order);

    /**
     * Hoàn tiền khi hủy đơn (chưa delivered):
     * - Wallet: hoàn toàn bộ totalAmount cho buyer TỪ admin escrow wallet
     *   (seller không bị động vì chưa nhận tiền)
     * - COD: không cần hoàn ví (tiền chưa chuyển qua hệ thống)
     */
    void refundCancelledOrder(Order order);

    /**
     * Hoàn tiền sau khi đơn đã delivered (seller đã nhận tiền):
     * - Wallet: hoàn toàn bộ totalAmount cho buyer bằng cách:
     *   + Trừ sellerReceivedAmount từ ví seller
     *   + Trừ commissionAmount từ ví admin
     *   + Cộng totalAmount cho buyer
     * - COD: hoàn buyer bằng cách trừ seller 88%, trừ admin 12%, cộng buyer
     *   (giả định seller đã nhận tiền mặt, admin đã thu commission từ ví seller)
     */
    void refundDeliveredOrder(Order order);

    /**
     * @deprecated Dùng refundCancelledOrder hoặc refundDeliveredOrder thay thế
     */
    @Deprecated
    void refundOrder(Order order);
}
