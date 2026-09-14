import 'package:flutter/material.dart';
import 'package:worthly_mobile/theme/colors.dart';

class RisingW extends StatelessWidget {
  const RisingW({super.key, this.size = 44, this.onDark = true});

  final double size;
  final bool onDark;

  @override
  Widget build(BuildContext context) {
    final left = onDark ? WorthlyColors.cream : WorthlyColors.pine;
    return SizedBox(
      width: size,
      height: size,
      child: CustomPaint(painter: _RisingWPainter(left: left, right: WorthlyColors.brass)),
    );
  }
}

class _RisingWPainter extends CustomPainter {
  _RisingWPainter({required this.left, required this.right});

  final Color left;
  final Color right;

  @override
  void paint(Canvas canvas, Size size) {
    final stroke = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = size.width * 0.087
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    final scale = size.width / 48;
    canvas.scale(scale);
    canvas.drawPath(
      Path()
        ..moveTo(7, 14)
        ..lineTo(15, 34)
        ..lineTo(24, 20),
      stroke..color = left,
    );
    canvas.drawPath(
      Path()
        ..moveTo(24, 20)
        ..lineTo(33, 34)
        ..lineTo(41, 8),
      stroke..color = right,
    );
  }

  @override
  bool shouldRepaint(covariant _RisingWPainter oldDelegate) => oldDelegate.left != left || oldDelegate.right != right;
}
