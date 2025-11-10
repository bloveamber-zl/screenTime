import 'package:flutter/material.dart';
import 'package:screen_time/screen_time.dart';

/// 应用阻止状态检测示例
/// 在你的应用中使用这个示例来检测阻止状态并显示相应的UI
class AppBlockingExample extends StatefulWidget {
  const AppBlockingExample({super.key});

  @override
  State<AppBlockingExample> createState() => _AppBlockingExampleState();
}

class _AppBlockingExampleState extends State<AppBlockingExample> {
  final ScreenTime _screenTime = ScreenTime();
  Map<String, dynamic>? _blockingStatus;
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _checkBlockingStatus();
  }

  /// 检查阻止状态
  Future<void> _checkBlockingStatus() async {
    setState(() {
      _isLoading = true;
    });

    try {
      final status = await _screenTime.getBlockingStatus();
      setState(() {
        _blockingStatus = status;
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _isLoading = false;
      });
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('检查阻止状态失败: $e')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    // 如果应用被阻止，显示阻止UI
    if (_blockingStatus != null && _blockingStatus!['isBlocked'] == true) {
      return _buildBlockingUI();
    }

    // 正常应用UI
    return _buildNormalUI();
  }

  /// 构建阻止状态UI
  Widget _buildBlockingUI() {
    final remainingTime = _blockingStatus!['remainingTimeFormatted'] ?? '00:00';
    final blockReason = _blockingStatus!['blockReason'] ?? '应用使用时间限制';
    final blockedApps = _blockingStatus!['blockedApps'] as List<dynamic>? ?? [];

    return Scaffold(
      backgroundColor: Colors.red.shade50,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              // 阻止图标
              Icon(Icons.block, size: 80, color: Colors.red.shade600),
              const SizedBox(height: 24),

              // 阻止标题
              Text(
                '应用使用受限',
                style: TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.bold,
                  color: Colors.red.shade800,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),

              // 阻止原因
              Text(
                blockReason,
                style: TextStyle(fontSize: 16, color: Colors.red.shade700),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 24),

              // 剩余时间
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 24,
                  vertical: 12,
                ),
                decoration: BoxDecoration(
                  color: Colors.red.shade100,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: Colors.red.shade300),
                ),
                child: Column(
                  children: [
                    Text(
                      '剩余时间',
                      style: TextStyle(
                        fontSize: 14,
                        color: Colors.red.shade600,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      remainingTime,
                      style: TextStyle(
                        fontSize: 32,
                        fontWeight: FontWeight.bold,
                        color: Colors.red.shade800,
                        fontFamily: 'monospace',
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),

              // 被阻止的应用列表
              if (blockedApps.isNotEmpty) ...[
                Text(
                  '被阻止的应用:',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                    color: Colors.red.shade700,
                  ),
                ),
                const SizedBox(height: 8),
                ...blockedApps.map(
                  (app) => Padding(
                    padding: const EdgeInsets.symmetric(vertical: 2),
                    child: Text(
                      '• $app',
                      style: TextStyle(
                        fontSize: 14,
                        color: Colors.red.shade600,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 24),
              ],

              // 刷新按钮
              ElevatedButton.icon(
                onPressed: _isLoading ? null : _checkBlockingStatus,
                icon:
                    _isLoading
                        ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                        : const Icon(Icons.refresh),
                label: Text(_isLoading ? '检查中...' : '刷新状态'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.red.shade600,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 24,
                    vertical: 12,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// 构建正常应用UI
  Widget _buildNormalUI() {
    return Scaffold(
      appBar: AppBar(
        title: const Text('我的应用'),
        backgroundColor: Colors.blue,
        foregroundColor: Colors.white,
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.check_circle, size: 80, color: Colors.green),
            const SizedBox(height: 24),
            const Text(
              '应用正常运行',
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.bold,
                color: Colors.green,
              ),
            ),
            const SizedBox(height: 16),
            const Text(
              '当前没有被阻止',
              style: TextStyle(fontSize: 16, color: Colors.grey),
            ),
            const SizedBox(height: 32),
            ElevatedButton.icon(
              onPressed: _isLoading ? null : _checkBlockingStatus,
              icon:
                  _isLoading
                      ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                      : const Icon(Icons.refresh),
              label: Text(_isLoading ? '检查中...' : '检查阻止状态'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.blue,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(
                  horizontal: 24,
                  vertical: 12,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// 使用说明：
/// 
/// 1. 在你的应用启动时调用 _checkBlockingStatus() 检查阻止状态
/// 2. 如果 isBlocked 为 true，显示阻止UI
/// 3. 如果 isBlocked 为 false，显示正常应用UI
/// 4. 可以定期调用 _checkBlockingStatus() 来更新状态
/// 
/// 返回的状态信息包含：
/// - isBlocked: 是否被阻止
/// - blockedApps: 被阻止的应用列表
/// - remainingTime: 剩余阻止时间（秒）
/// - blockReason: 阻止原因
/// - canOverride: 是否可以覆盖阻止
/// - remainingTimeFormatted: 格式化的剩余时间字符串
