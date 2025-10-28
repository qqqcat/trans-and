import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../presentation/history/history_page.dart';
import '../presentation/home/home_page.dart';
import '../presentation/settings/settings_page.dart';

final appRouterProvider = Provider<GoRouter>(
  (ref) => GoRouter(
    routes: [
      GoRoute(
        path: '/',
        name: HomePage.routeName,
        builder: (context, state) => const HomePage(),
        routes: [
          GoRoute(
            path: SettingsPage.routePath,
            name: SettingsPage.routeName,
            builder: (context, state) => const SettingsPage(),
          ),
          GoRoute(
            path: HistoryPage.routePath,
            name: HistoryPage.routeName,
            builder: (context, state) => const HistoryPage(),
          ),
        ],
      ),
    ],
  ),
);
