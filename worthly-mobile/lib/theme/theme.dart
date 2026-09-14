import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:worthly_mobile/theme/colors.dart';

ThemeData worthlyTheme() {
  final publicSans = GoogleFonts.publicSansTextTheme(ThemeData.light().textTheme).apply(
    bodyColor: WorthlyColors.ink,
    displayColor: WorthlyColors.ink,
  );
  return ThemeData(
    useMaterial3: true,
    scaffoldBackgroundColor: WorthlyColors.paper,
    colorScheme: const ColorScheme.light(
      primary: WorthlyColors.pine,
      onPrimary: WorthlyColors.cream,
      surface: WorthlyColors.paper,
      onSurface: WorthlyColors.ink,
    ),
    textTheme: publicSans,
    appBarTheme: const AppBarTheme(
      backgroundColor: WorthlyColors.paper,
      foregroundColor: WorthlyColors.ink,
      elevation: 0,
    ),
  );
}

TextStyle serif({double size = 42, Color color = WorthlyColors.cream}) {
  return GoogleFonts.instrumentSerif(fontSize: size, height: 1.05, color: color, fontWeight: FontWeight.w400);
}

TextStyle mono({double size = 12.5, Color color = WorthlyColors.ink, FontWeight weight = FontWeight.w500}) {
  return GoogleFonts.ibmPlexMono(fontSize: size, color: color, fontWeight: weight, letterSpacing: 0.02);
}

TextStyle labelStyle({Color color = WorthlyColors.faint}) {
  return GoogleFonts.ibmPlexMono(
    fontSize: 10,
    fontWeight: FontWeight.w500,
    letterSpacing: 1.4,
    color: color,
  );
}
