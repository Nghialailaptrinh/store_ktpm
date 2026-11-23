"use client";

import { useEffect, useState } from "react";
import adminApi from "@/features/admin/api/adminApi";
import type { OrderResponse } from "@/lib/types/api";

interface DashboardStatistics {
    totalActiveProducts: number;
    totalOrders: number;
    revenue: number;
    pendingOrders: number;
}

const initialStatistics: DashboardStatistics = {
    totalActiveProducts: 0,
    totalOrders: 0,
    revenue: 0,
    pendingOrders: 0,
};

function formatCurrency(amount: number): string {
    return amount.toLocaleString("vi-VN", {
        style: "currency",
        currency: "VND",
        maximumFractionDigits: 0,
    });
}

export default function AdminDashboard() {
    const [orders, setOrders] = useState<OrderResponse[]>([]);
    const [statistics, setStatistics] =
        useState<DashboardStatistics>(initialStatistics);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let isMounted = true;

        async function fetchDashboardData() {
            try {
                setIsLoading(true);
                setError(null);

                const [
                    productsResponse,
                    allOrdersResponse,
                    pendingOrdersResponse,
                    completedOrdersFirstPage,
                ] = await Promise.all([
                    // Endpoint hiện tại chỉ trả về sản phẩm ACTIVE.
                    adminApi.getProducts(0, 1),

                    // Lấy tổng số đơn và 5 đơn mới nhất.
                    adminApi.getOrders({
                        page: 0,
                        size: 5,
                    }),

                    // Lấy tổng số đơn PENDING.
                    adminApi.getOrders({
                        status: "PENDING",
                        page: 0,
                        size: 1,
                    }),

                    // Doanh thu được quy ước là tổng tiền đơn COMPLETED.
                    adminApi.getOrders({
                        status: "COMPLETED",
                        page: 0,
                        size: 100,
                    }),
                ]);

                let revenue = completedOrdersFirstPage.items.reduce(
                    (total, order) => total + order.totalAmount,
                    0
                );

                // Đọc các trang COMPLETED còn lại để tính đủ doanh thu.
                for (
                    let page = 1;
                    page < completedOrdersFirstPage.totalPages;
                    page += 1
                ) {
                    const completedOrdersPage = await adminApi.getOrders({
                        status: "COMPLETED",
                        page,
                        size: 100,
                    });

                    revenue += completedOrdersPage.items.reduce(
                        (total, order) => total + order.totalAmount,
                        0
                    );
                }

                if (!isMounted) {
                    return;
                }

                setOrders(allOrdersResponse.items);

                setStatistics({
                    totalActiveProducts: productsResponse.totalElements,
                    totalOrders: allOrdersResponse.totalElements,
                    revenue,
                    pendingOrders: pendingOrdersResponse.totalElements,
                });
            } catch (err) {
                if (!isMounted) {
                    return;
                }

                setError(
                    err instanceof Error
                        ? err.message
                        : "Không thể tải dữ liệu Dashboard"
                );
            } finally {
                if (isMounted) {
                    setIsLoading(false);
                }
            }
        }

        void fetchDashboardData();

        return () => {
            isMounted = false;
        };
    }, []);

    function renderStatistics() {
        return (
            <section className="dashboardSection">
                <h2 className="sectionTitle">Thống kê</h2>

                <div className="statisticsGrid">
                    <div className="statisticsCard">
                        <h3>Sản phẩm đang hoạt động</h3>
                        <p>
                            {isLoading
                                ? "Đang tải..."
                                : error
                                    ? "--"
                                    : statistics.totalActiveProducts.toLocaleString(
                                        "vi-VN"
                                    )}
                        </p>
                    </div>

                    <div className="statisticsCard">
                        <h3>Tổng đơn hàng</h3>
                        <p>
                            {isLoading
                                ? "Đang tải..."
                                : error
                                    ? "--"
                                    : statistics.totalOrders.toLocaleString(
                                        "vi-VN"
                                    )}
                        </p>
                    </div>

                    <div className="statisticsCard">
                        <h3>Doanh thu (đơn hoàn thành)</h3>
                        <p>
                            {isLoading
                                ? "Đang tải..."
                                : error
                                    ? "--"
                                    : formatCurrency(statistics.revenue)}
                        </p>
                    </div>

                    <div className="statisticsCard">
                        <h3>Đơn chờ xử lý</h3>
                        <p>
                            {isLoading
                                ? "Đang tải..."
                                : error
                                    ? "--"
                                    : statistics.pendingOrders.toLocaleString(
                                        "vi-VN"
                                    )}
                        </p>
                    </div>
                </div>

                {error && (
                    <p role="alert">
                        Không thể tải thống kê: {error}
                    </p>
                )}
            </section>
        );
    }

    function renderRecentOrders() {
        return (
            <section className="dashboardSection">
                <h2 className="sectionTitle">Đơn hàng gần đây</h2>

                <div className="recentOrdersTable">
                    <table>
                        <thead>
                        <tr>
                            <th>Mã đơn hàng</th>
                            <th>Người nhận</th>
                            <th>Ngày đặt</th>
                            <th>Tổng tiền</th>
                            <th>Trạng thái</th>
                        </tr>
                        </thead>

                        <tbody>
                        {isLoading ? (
                            <tr>
                                <td colSpan={5}>Đang tải dữ liệu...</td>
                            </tr>
                        ) : error ? (
                            <tr>
                                <td colSpan={5}>{error}</td>
                            </tr>
                        ) : orders.length === 0 ? (
                            <tr>
                                <td colSpan={5}>
                                    Chưa có dữ liệu đơn hàng
                                </td>
                            </tr>
                        ) : (
                            orders.map((order) => (
                                <tr key={order.id}>
                                    <td>{order.orderCode}</td>
                                    <td>{order.recipientName}</td>
                                    <td>
                                        {new Date(
                                            order.createdAt
                                        ).toLocaleString("vi-VN")}
                                    </td>
                                    <td>
                                        {formatCurrency(order.totalAmount)}
                                    </td>
                                    <td>{order.status}</td>
                                </tr>
                            ))
                        )}
                        </tbody>
                    </table>
                </div>
            </section>
        );
    }

    return (
        <main className="adminDashboard">
            <h1 className="dashboardTitle">Admin Dashboard</h1>

            {renderStatistics()}
            {renderRecentOrders()}
        </main>
    );
}