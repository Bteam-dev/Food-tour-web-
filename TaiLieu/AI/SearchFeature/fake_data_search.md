# Fake Data - search_analytics & search_histories

Chạy SQL này để tạo data giả cho 2 bảng phục vụ train PhoBERT và test hybrid search.

> **Yêu cầu:** Bảng `products` (ID 1-38) và `users` (ID 2-53) đã tồn tại.

---

## Bảng search_analytics (~250 rows)

Mô phỏng hành vi tìm kiếm thực tế:
- Mix user đã đăng nhập (user_id 2-53) và guest (NULL)
- Queries trending trong 7 ngày gần nhất: phở bò, cà phê sữa đá, bánh mì, bún bò Huế
- Click data đủ để train PhoBERT (click_position 0-4 là hợp lý)

```sql
INSERT INTO search_analytics
  (user_id, query_text, query_normalized, result_count, clicked_product_id, click_position, session_id, searched_at)
VALUES

-- ============================================================
-- PHỞ BÒ - trending (nhiều search gần đây)
-- ============================================================
(2,  'phở bò',           'pho bo',        12, 1,  0, 'sess_001', '2026-04-15 08:05:00'),
(3,  'phở bò',           'pho bo',        12, 4,  1, 'sess_002', '2026-04-15 08:30:00'),
(5,  'pho bo',           'pho bo',        12, 9,  0, 'sess_003', '2026-04-15 09:10:00'),
(NULL,'phở bò',          'pho bo',        12, 1,  0, 'sess_004', '2026-04-15 09:45:00'),
(7,  'phở bò tái',       'pho bo tai',    8,  4,  0, 'sess_005', '2026-04-15 10:20:00'),
(NULL,'pho',             'pho',           20, 9,  2, 'sess_006', '2026-04-15 11:00:00'),
(9,  'phở bò',           'pho bo',        12, 1,  0, 'sess_007', '2026-04-15 12:15:00'),
(11, 'phở',              'pho',           20, 10, 1, 'sess_008', '2026-04-15 13:30:00'),
(NULL,'poh bo',          'poh bo',        10, 4,  0, 'sess_009', '2026-04-15 14:00:00'),
(13, 'phở bò truyền thống','pho bo truyen thong', 5, 1, 0, 'sess_010', '2026-04-15 15:10:00'),
(2,  'phở gà',           'pho ga',        8,  10, 0, 'sess_011', '2026-04-15 16:20:00'),
(15, 'phở bò',           'pho bo',        12, 9,  0, 'sess_012', '2026-04-16 07:50:00'),
(NULL,'pho bo',          'pho bo',        12, 1,  1, 'sess_013', '2026-04-16 08:30:00'),
(17, 'phở',              'pho',           20, 4,  0, 'sess_014', '2026-04-16 09:15:00'),
(NULL,'poh',             'poh',           15, 9,  0, 'sess_015', '2026-04-16 10:00:00'),

-- 7-15 ngày trước
(4,  'phở bò',           'pho bo',        12, 1,  0, 'sess_016', '2026-04-10 08:00:00'),
(6,  'phở bò tái chín',  'pho bo tai chin', 6, 9, 0, 'sess_017', '2026-04-10 09:00:00'),
(8,  'phở gà',           'pho ga',        8,  10, 0, 'sess_018', '2026-04-11 08:30:00'),
(10, 'phở bò',           'pho bo',        12, 4,  2, 'sess_019', '2026-04-12 07:45:00'),
(NULL,'pho',             'pho',           20, 1,  1, 'sess_020', '2026-04-13 08:20:00'),

-- 15-30 ngày trước
(12, 'phở bò',           'pho bo',        12, 9,  0, 'sess_021', '2026-04-01 08:00:00'),
(14, 'phở gà',           'pho ga',        8,  10, 1, 'sess_022', '2026-04-03 09:30:00'),
(16, 'phở bò truyền thống','pho bo truyen thong', 5, 1, 0, 'sess_023', '2026-04-05 08:15:00'),

-- ============================================================
-- CÀ PHÊ - trending
-- ============================================================
(3,  'cà phê sữa đá',    'ca phe sua da', 6,  31, 0, 'sess_030', '2026-04-15 07:30:00'),
(NULL,'ca phe sua da',   'ca phe sua da', 6,  31, 0, 'sess_031', '2026-04-15 07:55:00'),
(5,  'cà phê',           'ca phe',        10, 30, 0, 'sess_032', '2026-04-15 08:00:00'),
(7,  'cà phê đen đá',    'ca phe den da', 5,  30, 0, 'sess_033', '2026-04-15 10:30:00'),
(9,  'cà phê sữa',       'ca phe sua',    7,  31, 1, 'sess_034', '2026-04-15 11:45:00'),
(NULL,'cf sua da',       'cf sua da',     4,  31, 0, 'sess_035', '2026-04-15 14:20:00'),
(11, 'cà phê sữa đá',    'ca phe sua da', 6,  31, 0, 'sess_036', '2026-04-16 07:00:00'),
(13, 'trà ô long',       'tra o long',    3,  32, 0, 'sess_037', '2026-04-16 08:45:00'),
(NULL,'cà phê',          'ca phe',        10, 30, 2, 'sess_038', '2026-04-16 09:30:00'),
(15, 'cà phê sữa đá',    'ca phe sua da', 6,  31, 0, 'sess_039', '2026-04-16 10:15:00'),
(17, 'coffee',           'coffee',        4,  30, 0, 'sess_040', '2026-04-14 08:00:00'),
(19, 'cà phê đen',       'ca phe den',    6,  30, 0, 'sess_041', '2026-04-13 09:00:00'),
(21, 'cà phê',           'ca phe',        10, 31, 1, 'sess_042', '2026-04-11 08:30:00'),
(NULL,'ca phe',          'ca phe',        10, 30, 0, 'sess_043', '2026-04-09 07:45:00'),
(23, 'cà phê sữa',       'ca phe sua',    7,  31, 0, 'sess_044', '2026-04-05 08:00:00'),
(25, 'trà đá chanh',     'tra da chanh',  5,  11, 0, 'sess_045', '2026-04-03 09:15:00'),

-- ============================================================
-- BÁNH MÌ - trending
-- ============================================================
(2,  'bánh mì',          'banh mi',       15, 15, 0, 'sess_050', '2026-04-15 07:00:00'),
(NULL,'banh mi thap cam','banh mi thap cam', 3, 15, 0, 'sess_051', '2026-04-15 07:30:00'),
(4,  'bánh mì thập cẩm', 'banh mi thap cam', 3, 15, 0, 'sess_052', '2026-04-15 09:00:00'),
(6,  'bánh mì pate',     'banh mi pate',  4,  5,  0, 'sess_053', '2026-04-15 11:20:00'),
(NULL,'banh mi',         'banh mi',       15, 16, 1, 'sess_054', '2026-04-15 12:00:00'),
(8,  'bánh mì xíu mại',  'banh mi xiu mai', 3, 16, 0, 'sess_055', '2026-04-15 13:30:00'),
(10, 'bánh mì',          'banh mi',       15, 5,  2, 'sess_056', '2026-04-16 07:15:00'),
(NULL,'bm thap cam',     'bm thap cam',   2,  15, 0, 'sess_057', '2026-04-16 08:00:00'),
(12, 'bánh mì',          'banh mi',       15, 15, 0, 'sess_058', '2026-04-16 09:45:00'),
(14, 'banh mi',          'banh mi',       15, 16, 0, 'sess_059', '2026-04-14 08:30:00'),
(16, 'bánh mì thập cẩm', 'banh mi thap cam', 3, 15, 0, 'sess_060', '2026-04-13 09:00:00'),
(18, 'bánh mì pate',     'banh mi pate',  4,  5,  1, 'sess_061', '2026-04-12 07:45:00'),
(NULL,'bánh mì',         'banh mi',       15, 15, 0, 'sess_062', '2026-04-10 08:00:00'),
(20, 'bánh mì',          'banh mi',       15, 16, 0, 'sess_063', '2026-04-07 09:00:00'),
(22, 'bánh mì thập cẩm', 'banh mi thap cam', 3, 15, 0, 'sess_064', '2026-04-04 08:15:00'),

-- ============================================================
-- BÚN BÒ HUẾ - trending
-- ============================================================
(3,  'bún bò Huế',       'bun bo hue',    6,  18, 0, 'sess_070', '2026-04-15 11:00:00'),
(5,  'bun bo hue',       'bun bo hue',    6,  18, 0, 'sess_071', '2026-04-15 12:30:00'),
(NULL,'bún bò',          'bun bo',        10, 8,  1, 'sess_072', '2026-04-15 13:00:00'),
(7,  'bún bò Huế đặc biệt','bun bo hue dac biet', 3, 18, 0, 'sess_073', '2026-04-15 14:30:00'),
(9,  'bún bò',           'bun bo',        10, 19, 2, 'sess_074', '2026-04-16 11:00:00'),
(NULL,'bun bo',          'bun bo',        10, 18, 0, 'sess_075', '2026-04-16 12:15:00'),
(11, 'bún bò Huế',       'bun bo hue',    6,  8,  0, 'sess_076', '2026-04-14 11:30:00'),
(13, 'bún bò',           'bun bo',        10, 18, 1, 'sess_077', '2026-04-13 12:00:00'),
(15, 'bún bò Huế',       'bun bo hue',    6,  18, 0, 'sess_078', '2026-04-11 11:45:00'),
(NULL,'bun bo',          'bun bo',        10, 19, 0, 'sess_079', '2026-04-09 12:30:00'),
(17, 'bún bò giò heo',   'bun bo gio heo', 4, 19, 0, 'sess_080', '2026-04-06 11:00:00'),
(19, 'bún bò',           'bun bo',        10, 8,  0, 'sess_081', '2026-04-03 12:00:00'),

-- ============================================================
-- MÌ QUẢNG
-- ============================================================
(2,  'mì Quảng',         'mi quang',      6,  12, 0, 'sess_090', '2026-04-15 12:00:00'),
(4,  'mi quang',         'mi quang',      6,  12, 0, 'sess_091', '2026-04-15 12:30:00'),
(NULL,'mì Quảng tôm thịt','mi quang tom thit', 3, 12, 0, 'sess_092', '2026-04-14 12:00:00'),
(6,  'mì Quảng gà',      'mi quang ga',   3,  13, 0, 'sess_093', '2026-04-13 12:15:00'),
(8,  'mì Quảng',         'mi quang',      6,  13, 1, 'sess_094', '2026-04-12 12:00:00'),
(NULL,'mi quang',        'mi quang',      6,  12, 0, 'sess_095', '2026-04-10 12:30:00'),
(10, 'mì Quảng tôm',     'mi quang tom',  4,  12, 0, 'sess_096', '2026-04-08 11:45:00'),
(12, 'mì Quảng',         'mi quang',      6,  12, 0, 'sess_097', '2026-04-05 12:00:00'),
(14, 'mi quang',         'mi quang',      6,  13, 0, 'sess_098', '2026-04-02 12:15:00'),

-- ============================================================
-- GỎI CUỐN
-- ============================================================
(3,  'gỏi cuốn',         'goi cuon',      5,  7,  0, 'sess_100', '2026-04-15 11:30:00'),
(NULL,'goi cuon',        'goi cuon',      5,  7,  0, 'sess_101', '2026-04-14 11:00:00'),
(5,  'gỏi cuốn tôm thịt','goi cuon tom thit', 3, 7, 0, 'sess_102', '2026-04-13 12:00:00'),
(7,  'goi cuon',         'goi cuon',      5,  7,  1, 'sess_103', '2026-04-11 11:30:00'),
(NULL,'gỏi cuốn',        'goi cuon',      5,  7,  0, 'sess_104', '2026-04-09 12:00:00'),
(9,  'healthy',          'healthy',       8,  7,  2, 'sess_105', '2026-04-07 13:00:00'),
(11, 'gỏi cuốn',         'goi cuon',      5,  7,  0, 'sess_106', '2026-04-04 11:00:00'),

-- ============================================================
-- BỘT CHIÊN
-- ============================================================
(2,  'bột chiên',        'bot chien',     4,  6,  0, 'sess_110', '2026-04-15 16:00:00'),
(NULL,'bot chien',       'bot chien',     4,  6,  0, 'sess_111', '2026-04-14 15:30:00'),
(4,  'bột chiên giòn',   'bot chien gion', 3, 6,  0, 'sess_112', '2026-04-12 16:00:00'),
(6,  'bột chiên',        'bot chien',     4,  6,  1, 'sess_113', '2026-04-10 15:45:00'),
(NULL,'bột chiên',       'bot chien',     4,  6,  0, 'sess_114', '2026-04-07 16:30:00'),
(8,  'an vat',           'an vat',        10, 6,  3, 'sess_115', '2026-04-04 17:00:00'),

-- ============================================================
-- HẢI SẢN / LẨU
-- ============================================================
(3,  'lẩu hải sản',      'lau hai san',   4,  36, 0, 'sess_120', '2026-04-15 18:00:00'),
(NULL,'lau hai san',     'lau hai san',   4,  36, 0, 'sess_121', '2026-04-14 18:30:00'),
(5,  'lẩu',              'lau',           8,  36, 1, 'sess_122', '2026-04-13 19:00:00'),
(7,  'ốc hương',         'oc huong',      3,  27, 0, 'sess_123', '2026-04-12 18:00:00'),
(9,  'ghẹ hấp',          'ghe hap',       2,  28, 0, 'sess_124', '2026-04-11 18:30:00'),
(NULL,'lẩu hải sản',     'lau hai san',   4,  37, 1, 'sess_125', '2026-04-10 19:00:00'),
(11, 'hải sản',          'hai san',       10, 36, 0, 'sess_126', '2026-04-09 18:00:00'),
(13, 'ốc hương xào dừa', 'oc huong xao dua', 2, 27, 0, 'sess_127', '2026-04-07 18:30:00'),
(NULL,'lẩu cá lóc',      'lau ca loc',    3,  37, 0, 'sess_128', '2026-04-05 19:00:00'),
(15, 'ghẹ',              'ghe',           4,  28, 0, 'sess_129', '2026-04-03 18:00:00'),

-- ============================================================
-- CHÈ / MÓN NGỌT
-- ============================================================
(2,  'chè Thái',         'che thai',      5,  22, 0, 'sess_130', '2026-04-15 15:00:00'),
(NULL,'che thai',        'che thai',      5,  22, 0, 'sess_131', '2026-04-14 15:30:00'),
(4,  'chè',              'che',           12, 21, 1, 'sess_132', '2026-04-13 14:00:00'),
(6,  'chè đậu đỏ',       'che dau do',    4,  21, 0, 'sess_133', '2026-04-12 15:00:00'),
(NULL,'chè hạt lựu',     'che hat luu',   3,  20, 0, 'sess_134', '2026-04-11 15:30:00'),
(8,  'chè Thái',         'che thai',      5,  22, 0, 'sess_135', '2026-04-09 14:45:00'),
(10, 'tráng miệng',      'trang mieng',   8,  22, 2, 'sess_136', '2026-04-07 15:00:00'),
(12, 'chè',              'che',           12, 20, 0, 'sess_137', '2026-04-04 14:30:00'),

-- ============================================================
-- NƯỚC GIẢI KHÁT
-- ============================================================
(3,  'nước rau má',      'nuoc rau ma',   5,  2,  0, 'sess_140', '2026-04-15 13:00:00'),
(5,  'rau ma',           'rau ma',        5,  3,  1, 'sess_141', '2026-04-14 13:30:00'),
(NULL,'sinh tố bơ',      'sinh to bo',    4,  17, 0, 'sess_142', '2026-04-13 14:00:00'),
(7,  'sinh tố',          'sinh to',       8,  17, 0, 'sess_143', '2026-04-12 13:15:00'),
(9,  'nước mía',         'nuoc mia',      4,  14, 0, 'sess_144', '2026-04-11 14:00:00'),
(NULL,'nuoc dua',        'nuoc dua',      4,  26, 0, 'sess_145', '2026-04-10 13:30:00'),
(11, 'sữa đậu nành',     'sua dau nanh',  4,  23, 0, 'sess_146', '2026-04-09 14:00:00'),
(13, 'nước rau má',      'nuoc rau ma',   5,  2,  0, 'sess_147', '2026-04-07 13:45:00'),
(NULL,'nuoc sâm',        'nuoc sam',      3,  35, 0, 'sess_148', '2026-04-05 14:00:00'),
(15, 'trà đá chanh',     'tra da chanh',  4,  11, 0, 'sess_149', '2026-04-03 13:00:00'),
(17, 'sinh tố bơ',       'sinh to bo',    4,  17, 0, 'sess_150', '2026-04-01 14:30:00'),

-- ============================================================
-- BÁNH XÈO / BÁNH KHỌT / NEM
-- ============================================================
(2,  'bánh xèo',         'banh xeo',      4,  24, 0, 'sess_160', '2026-04-14 17:00:00'),
(4,  'banh xeo',         'banh xeo',      4,  24, 0, 'sess_161', '2026-04-12 17:30:00'),
(NULL,'bánh xèo tôm thịt','banh xeo tom thit', 2, 24, 0, 'sess_162', '2026-04-10 17:00:00'),
(6,  'bánh khọt',        'banh khot',     3,  25, 0, 'sess_163', '2026-04-08 17:30:00'),
(8,  'nem cua bể',       'nem cua be',    4,  33, 0, 'sess_164', '2026-04-06 18:00:00'),
(NULL,'nem cua be',      'nem cua be',    4,  33, 0, 'sess_165', '2026-04-04 17:45:00'),
(10, 'chả mực',          'cha muc',       3,  34, 0, 'sess_166', '2026-04-02 18:00:00'),
(12, 'bánh xèo',         'banh xeo',      4,  25, 1, 'sess_167', '2026-03-30 17:00:00'),

-- ============================================================
-- QUERIES KHÔNG CÓ KẾT QUẢ / TÌM CHUNG (không click)
-- ============================================================
(NULL,'ăn sáng ngon',    'an sang ngon',  15, NULL, NULL, 'sess_170', '2026-04-15 08:00:00'),
(2,  'đồ ăn healthy',    'do an healthy', 8,  NULL, NULL, 'sess_171', '2026-04-15 09:00:00'),
(4,  'món ngon rẻ',      'mon ngon re',   20, NULL, NULL, 'sess_172', '2026-04-14 10:00:00'),
(NULL,'an sang',         'an sang',       18, NULL, NULL, 'sess_173', '2026-04-14 08:30:00'),
(6,  'đồ uống mát',      'do uong mat',   12, NULL, NULL, 'sess_174', '2026-04-13 11:00:00'),
(NULL,'mon nuoc',        'mon nuoc',      10, NULL, NULL, 'sess_175', '2026-04-13 12:00:00'),
(8,  'street food',      'street food',   14, NULL, NULL, 'sess_176', '2026-04-12 09:00:00'),
(10, 'ăn vặt ngon',      'an vat ngon',   16, NULL, NULL, 'sess_177', '2026-04-11 15:00:00'),
(NULL,'traditional',     'traditional',   20, NULL, NULL, 'sess_178', '2026-04-10 10:00:00'),
(12, 'món ăn Huế',       'mon an hue',    8,  NULL, NULL, 'sess_179', '2026-04-09 12:00:00'),

-- ============================================================
-- QUERIES TYPO / SAI DẤU (test PhoBERT)
-- ============================================================
(NULL,'pho bo',          'pho bo',        12, 1,  0, 'sess_180', '2026-04-16 08:00:00'),
(NULL,'bun bo',          'bun bo',        10, 18, 0, 'sess_181', '2026-04-16 09:00:00'),
(NULL,'banh my',         'banh my',       10, 5,  0, 'sess_182', '2026-04-15 10:00:00'),
(NULL,'ca fe sua',       'ca fe sua',     6,  31, 0, 'sess_183', '2026-04-15 08:30:00'),
(NULL,'goi cuon tuoi',   'goi cuon tuoi', 4,  7,  0, 'sess_184', '2026-04-14 11:00:00'),
(NULL,'mi quang tom',    'mi quang tom',  4,  12, 0, 'sess_185', '2026-04-13 12:00:00'),
(NULL,'lau hai san',     'lau hai san',   4,  36, 0, 'sess_186', '2026-04-12 18:00:00'),
(NULL,'che thai',        'che thai',      5,  22, 0, 'sess_187', '2026-04-11 15:00:00'),
(NULL,'banh xeo',        'banh xeo',      4,  24, 0, 'sess_188', '2026-04-10 17:00:00'),
(NULL,'nuoc rau ma',     'nuoc rau ma',   5,  2,  0, 'sess_189', '2026-04-09 13:00:00'),

-- ============================================================
-- QUERIES LỊCH SỬ CŨ (>15 ngày) - dùng cho training nhưng không trending
-- ============================================================
(2,  'phở bò',           'pho bo',        12, 1,  0, 'sess_200', '2026-03-25 08:00:00'),
(3,  'bún bò Huế',       'bun bo hue',    6,  18, 0, 'sess_201', '2026-03-25 11:00:00'),
(4,  'cà phê sữa đá',    'ca phe sua da', 6,  31, 0, 'sess_202', '2026-03-26 07:30:00'),
(5,  'bánh mì',          'banh mi',       15, 15, 0, 'sess_203', '2026-03-26 08:00:00'),
(6,  'mì Quảng',         'mi quang',      6,  12, 0, 'sess_204', '2026-03-27 12:00:00'),
(7,  'gỏi cuốn',         'goi cuon',      5,  7,  0, 'sess_205', '2026-03-27 11:30:00'),
(8,  'lẩu hải sản',      'lau hai san',   4,  36, 0, 'sess_206', '2026-03-28 18:00:00'),
(9,  'chè Thái',         'che thai',      5,  22, 0, 'sess_207', '2026-03-28 15:00:00'),
(10, 'phở bò',           'pho bo',        12, 4,  0, 'sess_208', '2026-03-29 08:00:00'),
(11, 'cà phê',           'ca phe',        10, 30, 0, 'sess_209', '2026-03-29 07:30:00'),
(12, 'bánh mì thập cẩm', 'banh mi thap cam', 3, 15, 0, 'sess_210', '2026-03-30 08:00:00'),
(13, 'bột chiên',        'bot chien',     4,  6,  0, 'sess_211', '2026-03-30 16:00:00'),
(14, 'ốc hương',         'oc huong',      3,  27, 0, 'sess_212', '2026-03-31 18:00:00'),
(15, 'sinh tố bơ',       'sinh to bo',    4,  17, 0, 'sess_213', '2026-03-31 14:00:00'),
(16, 'bún bò',           'bun bo',        10, 8,  0, 'sess_214', '2026-04-01 11:00:00'),
(17, 'phở gà',           'pho ga',        8,  10, 0, 'sess_215', '2026-04-01 08:30:00'),
(18, 'bánh xèo',         'banh xeo',      4,  24, 0, 'sess_216', '2026-04-02 17:00:00'),
(19, 'nem cua bể',       'nem cua be',    4,  33, 0, 'sess_217', '2026-04-02 10:00:00'),
(20, 'mì Quảng tôm thịt','mi quang tom thit', 3, 12, 0, 'sess_218', '2026-04-03 12:00:00'),
(21, 'nước rau má',      'nuoc rau ma',   5,  2,  0, 'sess_219', '2026-04-03 13:00:00');
```

---

## Bảng search_histories (~100 rows)

Lịch sử tìm kiếm của user đã đăng nhập. Mỗi user có 2-4 queries gần đây.

```sql
INSERT INTO search_histories
  (user_id, query_text, query_normalized, result_count, searched_at)
VALUES
-- User 2 - Minh Trần
(2, 'phở bò',           'pho bo',          12, '2026-04-15 08:05:00'),
(2, 'gỏi cuốn',         'goi cuon',         5, '2026-04-14 11:30:00'),
(2, 'bánh mì',          'banh mi',         15, '2026-04-13 08:00:00'),

-- User 3 - Minh Trần User
(3, 'cà phê sữa đá',    'ca phe sua da',    6, '2026-04-15 07:30:00'),
(3, 'bún bò Huế',       'bun bo hue',       6, '2026-04-14 11:00:00'),
(3, 'mì Quảng',         'mi quang',         6, '2026-04-12 12:00:00'),

-- User 4
(4, 'phở bò',           'pho bo',          12, '2026-04-15 08:00:00'),
(4, 'bánh mì thập cẩm', 'banh mi thap cam', 3, '2026-04-13 08:00:00'),

-- User 5
(5, 'pho bo',           'pho bo',          12, '2026-04-15 09:10:00'),
(5, 'ca phe sua da',    'ca phe sua da',    6, '2026-04-14 07:30:00'),
(5, 'gỏi cuốn',         'goi cuon',         5, '2026-04-12 11:30:00'),

-- User 6
(6, 'bột chiên',        'bot chien',        4, '2026-04-15 16:00:00'),
(6, 'bánh mì pate',     'banh mi pate',     4, '2026-04-13 08:00:00'),

-- User 7
(7, 'phở bò tái',       'pho bo tai',       8, '2026-04-15 10:20:00'),
(7, 'bún bò Huế đặc biệt','bun bo hue dac biet', 3, '2026-04-14 11:30:00'),
(7, 'ốc hương',         'oc huong',         3, '2026-04-12 18:00:00'),

-- User 8
(8, 'bún bò',           'bun bo',          10, '2026-04-15 11:00:00'),
(8, 'mì Quảng gà',      'mi quang ga',      3, '2026-04-13 12:00:00'),

-- User 9
(9, 'cà phê sữa',       'ca phe sua',       7, '2026-04-15 11:45:00'),
(9, 'ghẹ hấp',          'ghe hap',          2, '2026-04-13 18:30:00'),
(9, 'phở bò',           'pho bo',          12, '2026-04-11 08:00:00'),

-- User 10
(10, 'phở',             'pho',             20, '2026-04-15 13:30:00'),
(10, 'bánh mì',         'banh mi',         15, '2026-04-13 08:30:00'),

-- User 11
(11, 'chè Thái',        'che thai',         5, '2026-04-15 15:00:00'),
(11, 'sữa đậu nành',    'sua dau nanh',     4, '2026-04-14 14:00:00'),
(11, 'bún bò Huế',      'bun bo hue',       6, '2026-04-12 11:00:00'),

-- User 12
(12, 'mì Quảng',        'mi quang',         6, '2026-04-15 12:00:00'),
(12, 'lẩu hải sản',     'lau hai san',      4, '2026-04-13 18:00:00'),

-- User 13
(13, 'phở bò',          'pho bo',          12, '2026-04-15 15:10:00'),
(13, 'nước rau má',     'nuoc rau ma',      5, '2026-04-14 13:00:00'),
(13, 'bánh mì thập cẩm','banh mi thap cam', 3, '2026-04-12 08:00:00'),

-- User 14
(14, 'nước mía',        'nuoc mia',         4, '2026-04-15 14:00:00'),
(14, 'mì Quảng',        'mi quang',         6, '2026-04-13 12:00:00'),

-- User 15
(15, 'bánh mì',         'banh mi',         15, '2026-04-16 07:15:00'),
(15, 'cà phê sữa đá',   'ca phe sua da',    6, '2026-04-15 07:00:00'),
(15, 'gỏi cuốn',        'goi cuon',         5, '2026-04-13 11:00:00'),

-- User 16
(16, 'bánh mì xíu mại', 'banh mi xiu mai',  3, '2026-04-15 13:30:00'),
(16, 'phở bò',          'pho bo',          12, '2026-04-13 08:00:00'),

-- User 17
(17, 'sinh tố bơ',      'sinh to bo',       4, '2026-04-15 14:00:00'),
(17, 'cà phê đen đá',   'ca phe den da',    5, '2026-04-14 07:30:00'),

-- User 18
(18, 'bánh xèo',        'banh xeo',         4, '2026-04-15 17:00:00'),
(18, 'bún bò Huế đặc biệt','bun bo hue dac biet', 3, '2026-04-14 11:00:00'),

-- User 19
(19, 'phở',             'pho',             20, '2026-04-15 16:20:00'),
(19, 'nước rau má',     'nuoc rau ma',      5, '2026-04-13 13:00:00'),
(19, 'chè đậu đỏ',      'che dau do',       4, '2026-04-11 15:00:00'),

-- User 20
(20, 'bánh mì',         'banh mi',         15, '2026-04-15 08:00:00'),
(20, 'chè Thái',        'che thai',         5, '2026-04-13 15:00:00'),

-- Users 21-30 (gộp ngắn gọn)
(21, 'phở bò',          'pho bo',          12, '2026-04-14 08:00:00'),
(21, 'cà phê',          'ca phe',          10, '2026-04-12 07:30:00'),
(22, 'bánh mì thập cẩm','banh mi thap cam', 3, '2026-04-14 08:00:00'),
(22, 'chè Thái',        'che thai',         5, '2026-04-13 15:00:00'),
(23, 'mì Quảng',        'mi quang',         6, '2026-04-13 12:00:00'),
(23, 'cà phê sữa đá',   'ca phe sua da',    6, '2026-04-12 07:30:00'),
(24, 'bún bò Huế',      'bun bo hue',       6, '2026-04-13 11:00:00'),
(24, 'phở gà',          'pho ga',           8, '2026-04-11 08:30:00'),
(25, 'gỏi cuốn',        'goi cuon',         5, '2026-04-12 11:30:00'),
(25, 'lẩu hải sản',     'lau hai san',      4, '2026-04-10 18:00:00'),
(26, 'nem cua bể',      'nem cua be',       4, '2026-04-12 10:00:00'),
(26, 'ốc hương xào dừa','oc huong xao dua', 2, '2026-04-10 18:30:00'),
(27, 'sinh tố bơ',      'sinh to bo',       4, '2026-04-11 14:00:00'),
(27, 'bánh mì',         'banh mi',         15, '2026-04-09 08:00:00'),
(28, 'bột chiên',       'bot chien',        4, '2026-04-11 16:00:00'),
(28, 'trà đá chanh',    'tra da chanh',     5, '2026-04-09 09:00:00'),
(29, 'phở bò',          'pho bo',          12, '2026-04-10 08:00:00'),
(29, 'nước dừa tươi',   'nuoc dua tuoi',    3, '2026-04-08 14:00:00'),
(30, 'cà phê sữa đá',   'ca phe sua da',    6, '2026-04-10 07:30:00'),
(30, 'bánh khọt',       'banh khot',        3, '2026-04-08 17:30:00'),

-- Users 31-40
(31, 'phở bò',          'pho bo',          12, '2026-04-09 08:00:00'),
(32, 'mì Quảng tôm thịt','mi quang tom thit', 3, '2026-04-08 12:00:00'),
(33, 'bánh mì',         'banh mi',         15, '2026-04-08 08:00:00'),
(34, 'bún bò Huế',      'bun bo hue',       6, '2026-04-07 11:00:00'),
(35, 'cà phê',          'ca phe',          10, '2026-04-07 07:30:00'),
(36, 'chè Thái',        'che thai',         5, '2026-04-06 15:00:00'),
(37, 'gỏi cuốn',        'goi cuon',         5, '2026-04-06 11:30:00'),
(38, 'lẩu hải sản',     'lau hai san',      4, '2026-04-05 18:00:00'),
(39, 'phở gà',          'pho ga',           8, '2026-04-05 08:30:00'),
(40, 'sinh tố bơ',      'sinh to bo',       4, '2026-04-04 14:00:00');
```

---

## Cách test sau khi insert

### 1. Verify data đã vào đúng

```sql
-- Tổng rows
SELECT COUNT(*) FROM search_analytics;
SELECT COUNT(*) FROM search_histories;

-- Rows có click (dùng để train PhoBERT)
SELECT COUNT(*) FROM search_analytics WHERE clicked_product_id IS NOT NULL;

-- Top trending queries (7 ngày gần nhất)
SELECT query_text, COUNT(*) as cnt
FROM search_analytics
WHERE searched_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
GROUP BY query_normalized, query_text
ORDER BY cnt DESC
LIMIT 10;

-- Click rate theo product
SELECT p.name, COUNT(sa.id) as clicks
FROM search_analytics sa
JOIN products p ON sa.clicked_product_id = p.id
GROUP BY p.id, p.name
ORDER BY clicks DESC
LIMIT 10;
```

### 2. Test trending API

```bash
# Phải trả về: phở bò, cà phê sữa đá, bánh mì, bún bò Huế (cao nhất)
curl "http://localhost:8080/api/public/search/trending?limit=10"
```

### 3. Sync suggestion index từ data vừa insert

```bash
python scripts/search/sync_es_suggestions.py --recreate-index --from-db
python scripts/search/sync_es_suggestions.py --verify
```

### 4. Test smart suggestions

```bash
# BM25: prefix match
curl "http://localhost:8080/api/public/search/suggest?q=pho&limit=8"
curl "http://localhost:8080/api/public/search/suggest?q=banh&limit=8"

# PhoBERT: typo/no diacritics (cần Hybrid mode)
curl "http://localhost:8080/api/public/search/suggest?q=poh&limit=8"
curl "http://localhost:8080/api/public/search/suggest?q=ca fe&limit=8"
```

### 5. Test search với data có click

```bash
# Hybrid search (cần PhoBERT) - thử typo
curl "http://localhost:8080/api/public/products?keyword=poh+bo&page=0&size=5"

# BM25 - tìm bình thường
curl "http://localhost:8080/api/public/products?keyword=phở+bò&page=0&size=5"
```

### 6. Export CSV để train Colab

```sql
-- Export search_analytics.csv (dùng cho Bước 1 trong SearchRecommend.md)
SELECT id, user_id, query_text, query_normalized,
       clicked_product_id, click_position, result_count,
       session_id, searched_at
FROM search_analytics
WHERE query_text IS NOT NULL AND query_text != ''
ORDER BY searched_at DESC;
```

---

## Ghi chú

- Password tất cả users fake đều là `123456` (hash `$2a$10$faggoD8w4iseR...`)
- Product IDs dùng trong click data: 1-38 (khớp với data thực)
- User IDs: 2-53 (ID 1 là Admin, bỏ qua; ID 54-63 là sellers)
- `session_id` format `sess_NNN` chỉ để dễ đọc, trong production sẽ là UUID
