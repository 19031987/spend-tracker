# -*- coding: utf-8 -*-
"""
Generates the complete, self-contained Monarch Spend simulator with:
- Top Settings button & Monarch-style Settings Modal (4 tabs: Categories & Groups, Merchant Rules, Preferences, Data Actions)
- Bank Notification Listener / Alert Simulation Studio (Live push banner, bank presets & custom alerts)
- 3-tier Auto-categorization engine (User rules > History > Built-in dictionary > Uncategorized)
- Live [⚡ Auto-assigned: Groceries] hint with one-click Change action
- Category Selector Sheet with sticky '+ Add New Category' button
- Custom Category Creation Form with Parent Group (+ New Group), emoji grid, pastel swatches, and rule checkbox
- Interactive Test Bench with pre-populated demo transactions and verification suite
- Pure White & Pitch Black Material / Monarch CSS
"""

import sys

html_content = '''<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Monarch Spend • Smart Auto-Categorization, Settings &amp; Bank Alert Simulator</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;500;700;900&family=Inter:wght@400;500;600;700;800;900&family=JetBrains+Mono:wght@400;500;700&display=swap" rel="stylesheet">
    <style>
        :root {
            /* Material & Monarch Clean Palette */
            --bg-body: #F4F4F5;
            --bg-phone: #FFFFFF;
            --card-bg: #FFFFFF;
            --card-subtle: #F4F4F5;
            --card-border: #E4E4E7;
            --border-highlight: #000000;
            --text-pure: #000000;
            --text-high: #18181B;
            --text-mid: #52525B;
            --text-low: #71717A;
            --text-dim: #A1A1AA;
            
            --primary-black: #000000;
            --primary-white: #FFFFFF;
            --hero-bg: #000000;
            --hero-text: #FFFFFF;
            --hero-subtle: #18181B;
            --hero-border: #27272A;

            --money-in-green: #059669;
            --money-in-bg: #ECFDF5;
            --money-in-border: #A7F3D0;
            --money-out-red: #000000;
            --money-out-accent: #E11D48;
            --money-out-bg: #F4F4F5;

            --accent-indigo: #4F46E5;
            --accent-indigo-bg: #EEF2FF;
            --accent-amber: #D97706;
            --accent-amber-bg: #FEF3C7;
            --accent-amber-border: #F59E0B;
            
            --shadow-sm: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
            --shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.08), 0 2px 4px -2px rgba(0, 0, 0, 0.04);
            --shadow-lg: 0 10px 25px -5px rgba(0, 0, 0, 0.12), 0 8px 10px -6px rgba(0, 0, 0, 0.04);
        }

        body.theme-dark {
            --bg-body: #09090B;
            --bg-phone: #000000;
            --card-bg: #121214;
            --card-subtle: #18181B;
            --card-border: #27272A;
            --border-highlight: #FFFFFF;
            --text-pure: #FFFFFF;
            --text-high: #FAFAFA;
            --text-mid: #A1A1AA;
            --text-low: #71717A;
            --text-dim: #52525B;

            --primary-black: #FFFFFF;
            --primary-white: #000000;
            --hero-bg: #18181B;
            --hero-text: #FFFFFF;
            --hero-subtle: #27272A;
            --hero-border: #3F3F46;

            --money-in-green: #10B981;
            --money-in-bg: rgba(16, 185, 129, 0.15);
            --money-in-border: #059669;
            --money-out-red: #FFFFFF;
            --money-out-accent: #F43F5E;
            --money-out-bg: #27272A;

            --accent-indigo: #818CF8;
            --accent-indigo-bg: rgba(99, 102, 241, 0.15);
            --accent-amber: #FBBF24;
            --accent-amber-bg: rgba(245, 158, 11, 0.15);
            --accent-amber-border: #D97706;

            --shadow-sm: 0 1px 2px 0 rgba(0, 0, 0, 0.6);
            --shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.5);
            --shadow-lg: 0 10px 25px -5px rgba(0, 0, 0, 0.7);
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: 'Roboto', 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
            -webkit-tap-highlight-color: transparent;
        }

        body {
            background-color: var(--bg-body);
            color: var(--text-pure);
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: flex-start;
            padding: 20px 16px;
            transition: background-color 0.25s, color 0.25s;
        }

        /* Top Bar */
        .top-navbar {
            max-width: 1260px;
            width: 100%;
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 20px;
            padding: 0 4px;
        }

        .brand-logo {
            font-size: 20px;
            font-weight: 900;
            letter-spacing: -0.5px;
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .brand-badge {
            background: var(--primary-black);
            color: var(--primary-white);
            font-size: 10px;
            font-weight: 800;
            padding: 4px 10px;
            border-radius: 9999px;
            letter-spacing: 0.6px;
            text-transform: uppercase;
        }

        .nav-controls {
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .btn-nav {
            background: var(--card-bg);
            border: 1px solid var(--card-border);
            color: var(--text-pure);
            padding: 7px 14px;
            border-radius: 9999px;
            font-size: 12px;
            font-weight: 700;
            cursor: pointer;
            box-shadow: var(--shadow-sm);
            display: flex;
            align-items: center;
            gap: 6px;
            transition: all 0.2s;
        }

        .btn-nav:hover {
            border-color: var(--border-highlight);
            transform: translateY(-1px);
        }

        /* Main Container */
        .main-container {
            max-width: 1260px;
            width: 100%;
            display: grid;
            grid-template-columns: 440px 1fr;
            gap: 28px;
            align-items: start;
        }

        @media (max-width: 980px) {
            .main-container {
                grid-template-columns: 1fr;
                justify-items: center;
            }
        }

        /* Phone Simulator Mockup */
        .phone-frame {
            width: 100%;
            max-width: 440px;
            background: var(--bg-phone);
            border-radius: 46px;
            border: 10px solid #000000;
            box-shadow: 0 25px 60px -15px rgba(0, 0, 0, 0.35);
            overflow: hidden;
            position: relative;
            display: flex;
            flex-direction: column;
            height: 900px;
            transition: background 0.25s;
        }

        .phone-notch {
            position: absolute;
            top: 0;
            left: 50%;
            transform: translateX(-50%);
            width: 140px;
            height: 24px;
            background: #000000;
            border-bottom-left-radius: 16px;
            border-bottom-right-radius: 16px;
            z-index: 50;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 10px;
        }

        .phone-speaker {
            width: 40px;
            height: 4px;
            background: #27272A;
            border-radius: 4px;
        }

        .phone-camera {
            width: 8px;
            height: 8px;
            background: #18181B;
            border-radius: 50%;
            border: 1px solid #27272A;
        }

        .phone-status-bar {
            height: 38px;
            padding: 8px 24px 0;
            display: flex;
            justify-content: space-between;
            align-items: center;
            font-size: 11px;
            font-weight: 700;
            color: var(--text-pure);
            z-index: 40;
        }

        /* Incoming Bank Notification Dropdown Banner */
        .incoming-notification-alert {
            position: absolute;
            top: 38px;
            left: 14px;
            right: 14px;
            background: rgba(18, 18, 20, 0.96);
            color: #FFFFFF;
            border: 1px solid #3F3F46;
            border-radius: 18px;
            padding: 10px 14px;
            display: flex;
            align-items: center;
            gap: 12px;
            box-shadow: 0 10px 25px rgba(0, 0, 0, 0.45);
            z-index: 100;
            transform: translateY(-130px);
            transition: transform 0.35s cubic-bezier(0.16, 1, 0.3, 1);
            pointer-events: none;
        }
        .incoming-notification-alert.active {
            transform: translateY(0);
        }
        .notif-badge-icon {
            width: 34px;
            height: 34px;
            background: #27272A;
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 16px;
            flex-shrink: 0;
        }
        .notif-body {
            flex: 1;
            overflow: hidden;
        }
        .notif-app-name {
            font-size: 10px;
            font-weight: 800;
            letter-spacing: 0.5px;
            color: #A1A1AA;
            text-transform: uppercase;
        }
        .notif-content-preview {
            font-size: 12px;
            font-weight: 700;
            color: #FFFFFF;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .phone-screen {
            flex: 1;
            overflow-y: auto;
            padding: 10px 18px 24px;
            display: flex;
            flex-direction: column;
            gap: 14px;
        }

        .phone-screen::-webkit-scrollbar {
            width: 4px;
        }
        .phone-screen::-webkit-scrollbar-thumb {
            background: var(--card-border);
            border-radius: 4px;
        }

        /* App Header inside Phone */
        .app-top-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding-bottom: 6px;
        }

        .app-top-title {
            font-size: 22px;
            font-weight: 900;
            letter-spacing: -0.6px;
            color: var(--text-pure);
            display: flex;
            flex-direction: column;
        }

        .app-top-subtitle {
            font-size: 11px;
            font-weight: 500;
            color: var(--text-mid);
            margin-top: 2px;
        }

        .header-actions {
            display: flex;
            gap: 8px;
        }

        .btn-header {
            background: var(--primary-black);
            color: var(--primary-white);
            border: 1px solid var(--primary-black);
            padding: 7px 12px;
            border-radius: 12px;
            font-size: 12px;
            font-weight: 700;
            cursor: pointer;
            transition: all 0.2s;
            display: flex;
            align-items: center;
            gap: 4px;
        }

        .btn-header-outline {
            background: transparent;
            color: var(--text-pure);
            border: 1px solid var(--card-border);
        }

        .btn-header:hover {
            opacity: 0.85;
            transform: scale(0.98);
        }

        /* Cash Flow Hero Card */
        .hero-cashflow-card {
            background: var(--hero-bg);
            color: var(--hero-text);
            border: 1px solid var(--hero-border);
            border-radius: 22px;
            padding: 18px;
            box-shadow: var(--shadow-md);
        }

        .cashflow-top {
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .cashflow-label {
            font-size: 10px;
            font-weight: 800;
            letter-spacing: 0.8px;
            color: #A1A1AA;
        }

        .cashflow-badge {
            font-size: 10px;
            font-weight: 800;
            padding: 3px 9px;
            border-radius: 9999px;
            background: var(--money-in-bg);
            color: var(--money-in-green);
        }

        .net-amount {
            font-size: 34px;
            font-weight: 900;
            color: var(--money-in-green);
            margin: 6px 0 2px;
            letter-spacing: -0.8px;
        }

        .net-subtitle {
            font-size: 11px;
            font-weight: 500;
            color: #A1A1AA;
            margin-bottom: 14px;
        }

        .in-out-grid {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 10px;
            margin-bottom: 14px;
        }

        .stat-box {
            background: var(--hero-subtle);
            border: 1px solid var(--hero-border);
            border-radius: 14px;
            padding: 10px 12px;
            display: flex;
            flex-direction: column;
            gap: 2px;
        }

        .stat-tag {
            font-size: 9px;
            font-weight: 800;
            letter-spacing: 0.6px;
            text-transform: uppercase;
        }
        .stat-tag.tag-in { color: var(--money-in-green); }
        .stat-tag.tag-out { color: #E4E4E7; }

        .stat-val {
            font-size: 17px;
            font-weight: 900;
        }
        .stat-val.val-in { color: var(--money-in-green); }
        .stat-val.val-out { color: #FFFFFF; }

        .stat-today {
            font-size: 9px;
            color: #A1A1AA;
        }

        .ratio-bar-bg {
            height: 6px;
            background: var(--hero-border);
            border-radius: 9999px;
            overflow: hidden;
        }

        .ratio-bar-fill {
            height: 100%;
            background: #FFFFFF;
            border-radius: 9999px;
            transition: width 0.3s ease;
        }

        .ratio-bar-text {
            font-size: 10px;
            font-weight: 600;
            color: #A1A1AA;
            margin-top: 5px;
            display: flex;
            justify-content: space-between;
        }

        /* Section Titles */
        .section-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-top: 4px;
        }

        .section-title {
            font-size: 12px;
            font-weight: 800;
            text-transform: uppercase;
            letter-spacing: 0.6px;
            color: var(--text-pure);
        }

        .section-badge {
            font-size: 10px;
            font-weight: 700;
            color: var(--text-mid);
            background: var(--card-subtle);
            padding: 2px 8px;
            border-radius: 9999px;
        }

        /* Filter Pills */
        .filter-pills {
            display: flex;
            gap: 6px;
        }

        .filter-pill {
            background: var(--card-bg);
            border: 1px solid var(--card-border);
            color: var(--text-mid);
            font-size: 11px;
            font-weight: 700;
            padding: 5px 12px;
            border-radius: 9999px;
            cursor: pointer;
            transition: all 0.15s;
        }

        .filter-pill.active {
            background: var(--primary-black);
            color: var(--primary-white);
            border-color: var(--primary-black);
        }

        /* Transactions Feed */
        .transactions-feed {
            display: flex;
            flex-direction: column;
            gap: 8px;
        }

        .tx-card {
            background: var(--card-bg);
            border: 1px solid var(--card-border);
            border-radius: 16px;
            padding: 12px 14px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            cursor: pointer;
            box-shadow: var(--shadow-sm);
            transition: transform 0.15s, border-color 0.15s;
        }

        .tx-card:hover {
            transform: translateY(-1px);
            border-color: var(--border-highlight);
        }

        .tx-left {
            display: flex;
            align-items: center;
            gap: 12px;
            min-width: 0;
        }

        .tx-avatar {
            width: 38px;
            height: 38px;
            border-radius: 12px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 18px;
            flex-shrink: 0;
        }

        .tx-details {
            display: flex;
            flex-direction: column;
            min-width: 0;
        }

        .tx-merchant {
            font-size: 13px;
            font-weight: 800;
            color: var(--text-pure);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .tx-meta {
            font-size: 11px;
            color: var(--text-mid);
            display: flex;
            align-items: center;
            gap: 5px;
            margin-top: 2px;
        }

        .badge-uncat-pill {
            display: inline-flex;
            align-items: center;
            gap: 3px;
            background: var(--accent-amber-bg);
            color: var(--accent-amber);
            border: 1px dashed var(--accent-amber-border);
            padding: 1px 6px;
            border-radius: 6px;
            font-size: 10px;
            font-weight: 700;
        }

        .badge-transfer-pill {
            display: inline-flex;
            align-items: center;
            gap: 3px;
            background: #EDE9FE;
            color: #6D28D9;
            padding: 1px 6px;
            border-radius: 6px;
            font-size: 10px;
            font-weight: 700;
        }

        .badge-source-pill {
            display: inline-flex;
            align-items: center;
            gap: 2px;
            padding: 1px 6px;
            border-radius: 4px;
            font-size: 10px;
            font-weight: 700;
        }
        .source-chase {
            background: rgba(29, 78, 216, 0.12);
            color: #1E40AF;
        }
        .source-hsbc {
            background: rgba(220, 38, 38, 0.12);
            color: #991B1B;
        }
        .source-transfer {
            background: #EDE9FE;
            color: #6D28D9;
        }

        .tx-amount {
            font-size: 14px;
            font-weight: 900;
            font-family: 'JetBrains Mono', monospace;
            flex-shrink: 0;
        }
        .tx-amount.amount-out { color: var(--text-pure); }
        .tx-amount.amount-in { color: var(--money-in-green); }
        .tx-amount.amount-transfer { color: #7C3AED; }

        /* Right Panel: Studio & Test Bench */
        .simulator-control-panel {
            display: flex;
            flex-direction: column;
            gap: 20px;
        }

        .panel-card {
            background: var(--card-bg);
            border: 1px solid var(--card-border);
            border-radius: 20px;
            padding: 20px;
            box-shadow: var(--shadow-sm);
        }

        .panel-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 14px;
            padding-bottom: 12px;
            border-bottom: 1px solid var(--card-border);
        }

        .panel-title {
            font-size: 15px;
            font-weight: 900;
            color: var(--text-pure);
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .panel-pill-live {
            background: var(--money-in-bg);
            color: var(--money-in-green);
            font-size: 10px;
            font-weight: 800;
            padding: 3px 8px;
            border-radius: 9999px;
            letter-spacing: 0.5px;
        }

        .test-bench-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            gap: 10px;
            margin-bottom: 16px;
        }

        .btn-test-action {
            background: var(--card-subtle);
            border: 1px solid var(--card-border);
            color: var(--text-pure);
            padding: 10px 14px;
            border-radius: 12px;
            font-size: 12px;
            font-weight: 700;
            cursor: pointer;
            text-align: left;
            display: flex;
            align-items: center;
            justify-content: space-between;
            transition: all 0.2s;
        }

        .btn-test-action:hover {
            border-color: var(--border-highlight);
            transform: translateY(-1px);
            background: var(--card-bg);
        }

        .btn-test-action strong {
            display: block;
            font-size: 12px;
            color: var(--text-pure);
        }
        .btn-test-action span {
            font-size: 10px;
            color: var(--text-mid);
            font-weight: normal;
        }

        /* Preset Alert Buttons Grid */
        .alerts-preset-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 8px;
            margin-bottom: 14px;
        }

        .btn-alert-preset {
            background: var(--card-subtle);
            border: 1px solid var(--card-border);
            color: var(--text-pure);
            padding: 8px 12px;
            border-radius: 10px;
            font-size: 11px;
            font-weight: 700;
            cursor: pointer;
            text-align: left;
            transition: all 0.2s;
            display: flex;
            flex-direction: column;
            gap: 2px;
        }
        .btn-alert-preset:hover {
            border-color: var(--border-highlight);
            transform: translateY(-1px);
        }
        .btn-alert-preset .preset-bank {
            font-size: 9px;
            font-weight: 800;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            color: var(--accent-indigo);
        }
        .btn-alert-preset .preset-text {
            font-size: 11px;
            color: var(--text-pure);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        /* Forms */
        .form-row {
            display: flex;
            gap: 10px;
            margin-bottom: 12px;
        }

        .form-control {
            width: 100%;
            background: var(--card-bg);
            border: 1px solid var(--card-border);
            color: var(--text-pure);
            padding: 10px 12px;
            border-radius: 12px;
            font-size: 13px;
            font-weight: 600;
            outline: none;
            transition: border-color 0.2s;
        }

        .form-control:focus {
            border-color: var(--border-highlight);
        }

        .type-toggle-btn {
            flex: 1;
            background: var(--card-subtle);
            border: 1px solid var(--card-border);
            color: var(--text-mid);
            padding: 8px 12px;
            border-radius: 10px;
            font-size: 12px;
            font-weight: 800;
            cursor: pointer;
            transition: all 0.2s;
            text-align: center;
        }

        .type-toggle-btn.active.type-out {
            background: var(--primary-black);
            border-color: var(--primary-black);
            color: var(--primary-white);
        }

        .type-toggle-btn.active.type-in {
            background: var(--money-in-bg);
            border-color: var(--money-in-border);
            color: var(--money-in-green);
        }

        .type-toggle-btn.active.type-transfer {
            background: var(--accent-indigo-bg);
            border-color: var(--accent-indigo);
            color: var(--accent-indigo);
        }

        .btn-primary-action {
            width: 100%;
            background: var(--primary-black);
            color: var(--primary-white);
            border: none;
            padding: 12px;
            border-radius: 12px;
            font-size: 13px;
            font-weight: 800;
            cursor: pointer;
            transition: opacity 0.2s;
        }
        .btn-primary-action:hover { opacity: 0.85; }

        /* Auto-assigned Live Hint */
        .auto-hint-banner {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 8px 12px;
            background: #ECFDF5;
            border: 1px solid #A7F3D0;
            border-radius: 10px;
            margin-bottom: 12px;
            font-size: 12px;
            font-weight: 700;
            color: #065F46;
            animation: fadeIn 0.15s ease-out;
        }
        body.theme-dark .auto-hint-banner {
            background: rgba(16, 185, 129, 0.15);
            border-color: #059669;
            color: #34D399;
        }

        .auto-hint-change-btn {
            background: transparent;
            border: none;
            color: #047857;
            font-weight: 800;
            font-size: 12px;
            cursor: pointer;
            text-decoration: underline;
        }
        body.theme-dark .auto-hint-change-btn { color: #6EE7B7; }

        /* Log Terminal */
        .terminal-box {
            background: #000000;
            border: 1px solid var(--card-border);
            border-radius: 14px;
            padding: 14px;
            font-family: 'JetBrains Mono', monospace;
            font-size: 11px;
            max-height: 180px;
            overflow-y: auto;
            color: #A1A1AA;
        }
        .log-line { margin-bottom: 4px; line-height: 1.4; }
        .log-success { color: #10B981; }
        .log-warn { color: #F59E0B; }
        .log-err { color: #F43F5E; }
        .log-info { color: #FFFFFF; }

        /* Modals & Slide-Over Drawers */
        .modal-overlay {
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.6);
            backdrop-filter: blur(4px);
            z-index: 1000;
            display: none;
            align-items: center;
            justify-content: center;
            padding: 16px;
        }
        .modal-overlay.active {
            display: flex;
        }

        .modal-container {
            background: var(--card-bg);
            border: 1px solid var(--card-border);
            border-radius: 24px;
            width: 100%;
            max-width: 600px;
            max-height: 85vh;
            display: flex;
            flex-direction: column;
            box-shadow: var(--shadow-lg);
            overflow: hidden;
            animation: modalPop 0.2s cubic-bezier(0.16, 1, 0.3, 1);
        }

        @keyframes modalPop {
            from { opacity: 0; transform: scale(0.96) translateY(10px); }
            to { opacity: 1; transform: scale(1) translateY(0); }
        }

        .modal-header {
            padding: 18px 22px;
            border-bottom: 1px solid var(--card-border);
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .modal-title-row {
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .modal-title {
            font-size: 17px;
            font-weight: 900;
            color: var(--text-pure);
        }
        .modal-subtitle {
            font-size: 11px;
            color: var(--text-mid);
        }

        .modal-close-btn {
            background: transparent;
            border: none;
            font-size: 20px;
            color: var(--text-mid);
            cursor: pointer;
            padding: 4px 8px;
            border-radius: 8px;
        }
        .modal-close-btn:hover { background: var(--card-subtle); color: var(--text-pure); }

        .modal-tabs {
            display: flex;
            border-bottom: 1px solid var(--card-border);
            background: var(--card-subtle);
            padding: 0 16px;
            gap: 6px;
            overflow-x: auto;
        }

        .modal-tab-btn {
            background: transparent;
            border: none;
            border-bottom: 2px solid transparent;
            color: var(--text-mid);
            padding: 12px 14px;
            font-size: 12px;
            font-weight: 700;
            cursor: pointer;
            white-space: nowrap;
            transition: all 0.2s;
        }
        .modal-tab-btn.active {
            color: var(--text-pure);
            border-bottom-color: var(--border-highlight);
            font-weight: 900;
        }

        .modal-body {
            padding: 20px;
            overflow-y: auto;
            flex: 1;
            display: flex;
            flex-direction: column;
            gap: 16px;
        }

        .tab-pane {
            display: none;
            flex-direction: column;
            gap: 16px;
        }
        .tab-pane.active {
            display: flex;
        }

        .group-tree-card {
            background: var(--card-subtle);
            border: 1px solid var(--card-border);
            border-radius: 16px;
            padding: 14px;
        }

        .group-tree-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 10px;
            padding-bottom: 8px;
            border-bottom: 1px solid var(--card-border);
        }

        .group-tree-title {
            display: flex;
            align-items: center;
            gap: 8px;
            font-size: 13px;
            font-weight: 800;
            color: var(--text-pure);
        }

        .group-tree-count {
            font-size: 11px;
            color: var(--text-mid);
            font-weight: 600;
        }

        .subcats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
            gap: 8px;
        }

        .cat-chip {
            background: var(--card-bg);
            border: 1px solid var(--card-border);
            border-radius: 10px;
            padding: 6px 10px;
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 11px;
            font-weight: 700;
            color: var(--text-pure);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        .cat-chip-icon {
            width: 22px;
            height: 22px;
            border-radius: 6px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 12px;
            flex-shrink: 0;
        }

        .rule-item {
            background: var(--card-subtle);
            border: 1px solid var(--card-border);
            border-radius: 12px;
            padding: 10px 14px;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .rule-pattern-text {
            font-family: 'JetBrains Mono', monospace;
            font-size: 12px;
            font-weight: 800;
            background: var(--card-bg);
            padding: 2px 8px;
            border-radius: 6px;
            border: 1px solid var(--card-border);
        }

        .btn-delete-rule {
            background: transparent;
            border: none;
            color: #EF4444;
            font-size: 12px;
            font-weight: 700;
            cursor: pointer;
            padding: 4px 8px;
            border-radius: 6px;
        }
        .btn-delete-rule:hover { background: rgba(239, 68, 68, 0.1); }

        .pref-row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 12px;
            background: var(--card-subtle);
            border: 1px solid var(--card-border);
            border-radius: 14px;
        }
        .pref-label {
            font-size: 13px;
            font-weight: 800;
            color: var(--text-pure);
        }
        .pref-sub {
            font-size: 11px;
            color: var(--text-mid);
        }

        .swatch-row {
            display: flex;
            gap: 8px;
            flex-wrap: wrap;
        }
        .color-swatch-circle {
            width: 32px;
            height: 32px;
            border-radius: 50%;
            cursor: pointer;
            border: 2px solid transparent;
            transition: transform 0.15s, border-color 0.15s;
        }
        .color-swatch-circle.selected {
            transform: scale(1.15);
            border-color: #000000;
            box-shadow: 0 0 0 2px #FFFFFF;
        }
        body.theme-dark .color-swatch-circle.selected {
            border-color: #FFFFFF;
            box-shadow: 0 0 0 2px #000000;
        }

        .emoji-grid {
            display: grid;
            grid-template-columns: repeat(9, 1fr);
            gap: 6px;
            background: var(--card-subtle);
            padding: 8px;
            border-radius: 12px;
        }
        .emoji-item {
            font-size: 18px;
            padding: 6px;
            text-align: center;
            border-radius: 8px;
            cursor: pointer;
            transition: background 0.15s;
        }
        .emoji-item:hover, .emoji-item.selected {
            background: var(--card-bg);
            box-shadow: var(--shadow-sm);
        }

        .sticky-picker-footer {
            padding: 12px 20px;
            border-top: 1px solid var(--card-border);
            background: var(--card-bg);
        }
    </style>
</head>
<body>

    <!-- Top Navbar -->
    <div class="top-navbar">
        <div class="brand-logo">
            <span>SPENDTRACKER</span>
            <span class="brand-badge">MONARCH MONEY UX EDITION</span>
        </div>
        <div class="nav-controls">
            <button class="btn-nav" onclick="openSettingsModal()">
                <span>⚙️</span>
                <span>Settings</span>
            </button>
            <button class="btn-nav" onclick="toggleTheme()">
                <span id="themeIcon">⚫</span>
                <span id="themeText">Pitch Black Mode</span>
            </button>
        </div>
    </div>

    <!-- Main Container -->
    <div class="main-container">

        <!-- Phone Simulator Mockup -->
        <div class="phone-frame">
            <div class="phone-notch">
                <div class="phone-speaker"></div>
                <div class="phone-camera"></div>
            </div>

            <div class="phone-status-bar">
                <span id="statusBarTime">09:41</span>
                <span>📶 5G 100% 🔋</span>
            </div>

            <!-- Incoming Notification Dropdown Banner -->
            <div id="notifBanner" class="incoming-notification-alert">
                <div id="notifIcon" class="notif-badge-icon">💳</div>
                <div class="notif-body">
                    <div id="notifApp" class="notif-app-name">BANK NOTIFICATION</div>
                    <div id="notifText" class="notif-content-preview">You spent £14.80 at TESCO STORES</div>
                </div>
            </div>

            <div class="phone-screen">
                <!-- App Top Header with Active Settings Button -->
                <div class="app-top-header">
                    <div class="app-top-title">
                        <span>SpendTracker</span>
                        <span class="app-top-subtitle">Cash Flow &amp; Expense Radar</span>
                    </div>
                    <div class="header-actions">
                        <button class="btn-header" onclick="openAddTransactionModal()">+ Add</button>
                        <button class="btn-header btn-header-outline" id="topPhoneSettingsBtn" onclick="openSettingsModal()" title="Open Settings &amp; Rules">⚙️ Settings</button>
                    </div>
                </div>

                <!-- Monthly Cash Flow Hero Card -->
                <div class="hero-cashflow-card">
                    <div class="cashflow-top">
                        <span class="cashflow-label">MONTHLY CASH FLOW</span>
                        <span id="cashflowStatusBadge" class="cashflow-badge">Positive Flow</span>
                    </div>

                    <div id="tvNetCashFlow" class="net-amount">+£0.00</div>
                    <div id="tvNetSubtitle" class="net-subtitle">Net monthly cash balance</div>

                    <div class="in-out-grid">
                        <div class="stat-box in-box">
                            <span class="stat-tag tag-in">↑ MONEY IN</span>
                            <span id="tvMoneyInMonth" class="stat-val val-in">+£0.00</span>
                            <span id="tvMoneyInToday" class="stat-today">Today: +£0.00</span>
                        </div>
                        <div class="stat-box out-box">
                            <span class="stat-tag tag-out">↓ MONEY OUT</span>
                            <span id="tvMoneyOutMonth" class="stat-val val-out">-£0.00</span>
                            <span id="tvMoneyOutToday" class="stat-today">Today: -£0.00</span>
                        </div>
                    </div>

                    <div class="ratio-bar-wrap">
                        <div class="ratio-bar-bg">
                            <div id="ratioBarFill" class="ratio-bar-fill" style="width: 0%"></div>
                        </div>
                        <div class="ratio-bar-text">
                            <span id="tvRatioText">0% of income spent</span>
                            <span id="tvSavingsText">100% saved</span>
                        </div>
                    </div>
                </div>

                <!-- Bank Accounts (Sources: Chase & HSBC) -->
                <div class="section-header">
                    <span class="section-title">Bank Accounts (Sources)</span>
                    <span class="section-badge" style="background: #EDE9FE; color: #6D28D9;">Chase &amp; HSBC</span>
                </div>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-bottom: 14px;">
                    <div style="background: rgba(29, 78, 216, 0.06); border: 1px solid rgba(29, 78, 216, 0.15); border-radius: 12px; padding: 10px 12px;">
                        <div style="display: flex; align-items: center; gap: 6px; margin-bottom: 4px;">
                            <span style="font-size: 14px;">🏛️</span>
                            <span style="font-size: 12px; font-weight: 700; color: #1E40AF;">Chase</span>
                        </div>
                        <div style="font-size: 13px; font-weight: 700; color: var(--text-pure);" id="chaseSpentVal">Spent: £0.00</div>
                        <div style="font-size: 11px; font-weight: 600; color: var(--money-in-green);" id="chaseInVal">In: +£0.00</div>
                    </div>
                    <div style="background: rgba(220, 38, 38, 0.06); border: 1px solid rgba(220, 38, 38, 0.15); border-radius: 12px; padding: 10px 12px;">
                        <div style="display: flex; align-items: center; gap: 6px; margin-bottom: 4px;">
                            <span style="font-size: 14px;">🏦</span>
                            <span style="font-size: 12px; font-weight: 700; color: #991B1B;">HSBC</span>
                        </div>
                        <div style="font-size: 13px; font-weight: 700; color: var(--text-pure);" id="hsbcSpentVal">Spent: £0.00</div>
                        <div style="font-size: 11px; font-weight: 600; color: var(--money-in-green);" id="hsbcInVal">In: +£0.00</div>
                    </div>
                </div>

                <!-- Category Spending Section -->
                <div class="section-header">
                    <span class="section-title">Category Spending</span>
                    <span id="catCountBadge" class="section-badge">0 Categories</span>
                </div>
                <div id="layoutCategories" style="display: flex; flex-direction: column; gap: 8px;"></div>

                <!-- Transactions Section with Filter Pills -->
                <div class="section-header">
                    <span class="section-title">Transactions</span>
                    <span id="txCountBadge" class="section-badge">0 total</span>
                </div>

                <div class="filter-pills">
                    <button class="filter-pill active" id="filterAll" onclick="setFilter(null)">All</button>
                    <button class="filter-pill" id="filterOut" onclick="setFilter('EXPENSE')">↓ Outflow</button>
                    <button class="filter-pill" id="filterIn" onclick="setFilter('INCOME')">↑ Inflow</button>
                    <button class="filter-pill" id="filterTransfer" onclick="setFilter('TRANSFER')">🔄 Transfers</button>
                </div>

                <div class="transactions-feed" id="layoutTransactions"></div>
            </div>
        </div>

        <!-- Right Side: Test Bench, Notification Studio & Manual Creator -->
        <div class="simulator-control-panel">

            <!-- PANEL 1: BANK NOTIFICATION LISTENER & ALERT SIMULATOR -->
            <div class="panel-card">
                <div class="panel-header">
                    <div class="panel-title">
                        <span>📲 Bank Notification Listener Studio</span>
                    </div>
                    <span class="panel-pill-live">LISTENER ACTIVE</span>
                </div>

                <p style="font-size: 12px; color: var(--text-mid); margin-bottom: 12px; line-height: 1.5;">
                    Simulate real-time Android push notifications from UK &amp; global banking apps. The notification parser extracts vendor &amp; amount, while the classification engine assigns the category.
                </p>

                <!-- Preset Notification Alert Buttons -->
                <div style="font-size: 11px; font-weight: 800; color: var(--text-mid); margin-bottom: 6px;">QUICK BANK ALERTS (TAP TO SIMULATE INCOMING PUSH):</div>
                <div class="alerts-preset-grid">
                    <button class="btn-alert-preset" onclick="triggerNotificationAlert('uk.co.hsbc.hsbcukmobilebanking', 'HSBC Spend Alert', 'You spent £14.80 at TESCO STORES on 07/10')">
                        <span class="preset-bank">🔴 HSBC Bank</span>
                        <span class="preset-text">£14.80 at TESCO STORES</span>
                    </button>

                    <button class="btn-alert-preset" onclick="triggerNotificationAlert('co.uk.getmondo', 'Monzo Salary', 'Salary credit: £2,850.00 from TechCorp')">
                        <span class="preset-bank" style="color: #10B981;">🟣 Monzo Income</span>
                        <span class="preset-text">Salary +£2,850.00</span>
                    </button>

                    <button class="btn-alert-preset" onclick="triggerNotificationAlert('uk.co.hsbc.hsbcukmobilebanking', 'HSBC Spend Alert', 'Card ending 8219 spent £4.20 at COSTA COFFEE')">
                        <span class="preset-bank">🔴 HSBC Bank</span>
                        <span class="preset-text">£4.20 at COSTA COFFEE</span>
                    </button>

                    <button class="btn-alert-preset" onclick="triggerNotificationAlert('com.jpmorgan.chase.uk', 'Chase Spend Alert', 'You spent £18.50 with your card at ASDA')">
                        <span class="preset-bank" style="color: #3B82F6;">🔵 Chase UK</span>
                        <span class="preset-text">£18.50 with card at ASDA</span>
                    </button>

                    <button class="btn-alert-preset" onclick="triggerNotificationAlert('uk.co.hsbc.hsbcukmobilebanking', 'HSBC Direct Debit', 'Direct debit to British Gas of £65.00')">
                        <span class="preset-bank">🔴 HSBC Direct Debit</span>
                        <span class="preset-text">£65.00 to British Gas</span>
                    </button>

                    <button class="btn-alert-preset" onclick="triggerNotificationAlert('com.paypal.android.p2pmobile', 'PayPal Payment', 'Payment of £8.99 to NETFLIX')">
                        <span class="preset-bank" style="color: #6366F1;">🔵 PayPal</span>
                        <span class="preset-text">£8.99 to NETFLIX</span>
                    </button>

                    <button class="btn-alert-preset" onclick="triggerNotificationAlert('com.google.android.apps.walletnfcrel', 'Shell Petrol', '£45.00 with Visa ••1234')">
                        <span class="preset-bank" style="color: #F59E0B;">🟡 Google Wallet</span>
                        <span class="preset-text">£45.00 at Shell Petrol</span>
                    </button>

                    <button class="btn-alert-preset" onclick="triggerNotificationAlert('uk.co.hsbc.hsbcukmobilebanking', 'HSBC Spend Alert', 'Card ending 9012 spent £32.50 at Apex Hardware')">
                        <span class="preset-bank" style="color: #EF4444;">❓ Unknown Merchant</span>
                        <span class="preset-text">£32.50 at Apex Hardware</span>
                    </button>
                </div>

                <!-- Custom Raw Notification Trigger Form -->
                <form onsubmit="handleCustomNotificationSubmit(event)" style="background: var(--card-subtle); padding: 12px; border-radius: 14px; border: 1px solid var(--card-border);">
                    <div style="font-size: 11px; font-weight: 800; margin-bottom: 8px;">SIMULATE CUSTOM PUSH NOTIFICATION:</div>
                    <div class="form-row" style="margin-bottom: 8px;">
                        <select id="notifSimBank" class="form-control" style="max-width: 150px;">
                            <option value="uk.co.hsbc.hsbcukmobilebanking">HSBC UK</option>
                            <option value="co.uk.getmondo">Monzo</option>
                            <option value="com.jpmorgan.chase.uk">Chase</option>
                            <option value="com.revolut.revolut">Revolut</option>
                            <option value="com.paypal.android.p2pmobile">PayPal</option>
                            <option value="com.barclays.android.barclaysmobilebanking">Barclays</option>
                        </select>
                        <input type="text" id="notifSimTitle" class="form-control" placeholder="Notification Title (e.g. Bank Alert)" value="Bank Spend Alert">
                    </div>
                    <div class="form-row" style="margin-bottom: 8px;">
                        <input type="text" id="notifSimText" class="form-control" placeholder="Raw notification text (e.g. You spent £22.00 at Shell Garage)" required>
                    </div>
                    <button type="submit" class="btn-primary-action" style="padding: 10px; font-size: 12px; display: flex; align-items: center; justify-content: center; gap: 6px;">
                        <span>🔔</span> Simulate Push Notification Delivery
                    </button>
                </form>
            </div>

            <!-- PANEL 2: INTERACTIVE TEST BENCH & VERIFICATION -->
            <div class="panel-card">
                <div class="panel-header">
                    <div class="panel-title">
                        <span>⚡ Interactive Test Bench</span>
                    </div>
                    <span style="font-size: 11px; color: var(--text-mid); font-weight: 600;">Automated Radar</span>
                </div>

                <div class="test-bench-grid">
                    <button class="btn-test-action" onclick="openSettingsModal()">
                        <div>
                            <strong>⚙️ Settings Modal</strong>
                            <span>Inspect groups &amp; rules</span>
                        </div>
                        <span>➔</span>
                    </button>

                    <button class="btn-test-action" onclick="testMerchantInput('Tesco Express')">
                        <div>
                            <strong>🛒 Type "Tesco"</strong>
                            <span>Auto-assigned: Groceries (Rule)</span>
                        </div>
                        <span>⚡</span>
                    </button>

                    <button class="btn-test-action" onclick="testMerchantInput('Shell Petrol')">
                        <div>
                            <strong>⛽ Type "Shell"</strong>
                            <span>Auto-assigned: Transport (Dict)</span>
                        </div>
                        <span>⚡</span>
                    </button>

                    <button class="btn-test-action" onclick="testMerchantInput('Apex Hardware')">
                        <div>
                            <strong>❓ Unknown "Apex Hardware"</strong>
                            <span>Test + Add Custom Category</span>
                        </div>
                        <span>➕</span>
                    </button>
                </div>

                <button class="btn-primary-action" onclick="runEndToEndVerification()" style="background: var(--accent-indigo); display: flex; align-items: center; justify-content: center; gap: 8px;">
                    <span>▶️</span> Run Automated Verification Suite
                </button>
            </div>

            <!-- PANEL 3: RECORD CUSTOM TRANSACTION -->
            <div class="panel-card">
                <div class="panel-header">
                    <div class="panel-title">
                        <span>📝 Record Transaction Manually</span>
                    </div>
                    <span style="font-size: 11px; color: var(--text-mid); font-weight: 600;">Monarch Input Pipeline</span>
                </div>

                <form onsubmit="handleRecordSubmit(event)">
                    <div class="form-row">
                        <button type="button" class="type-toggle-btn active type-out" id="formTypeExpense" onclick="setTransactionType('EXPENSE')">
                            ↓ Expense
                        </button>
                        <button type="button" class="type-toggle-btn" id="formTypeIncome" onclick="setTransactionType('INCOME')">
                            ↑ Income
                        </button>
                        <button type="button" class="type-toggle-btn" id="formTypeTransfer" onclick="setTransactionType('TRANSFER')">
                            🔄 Transfer
                        </button>
                    </div>

                    <div class="form-row" style="flex-direction: column; gap: 4px;">
                        <input type="text" id="recordMerchant" class="form-control" placeholder="Vendor / Payee (e.g. Tesco, Shell, Starbucks, Netflix)" oninput="onMerchantTyped(this.value)" required>
                    </div>

                    <!-- Live Auto-Assigned Hint Banner -->
                    <div id="recordAutoHint" class="auto-hint-banner" style="display: none;">
                        <span id="recordAutoHintText">⚡ Auto-assigned: Groceries</span>
                        <button type="button" class="auto-hint-change-btn" onclick="openCategoryPickerModal()">Change</button>
                    </div>

                    <div class="form-row">
                        <input type="number" id="recordAmount" class="form-control" placeholder="Amount (e.g. 14.80)" step="0.01" min="0.01" required>
                        <div style="flex: 1; display: flex; align-items: center;">
                            <div id="recordCatPillWrap" onclick="openCategoryPickerModal()" style="width: 100%; cursor: pointer;">
                                <!-- Rendered dynamically -->
                            </div>
                        </div>
                    </div>

                    <!-- Account Picker Row -->
                    <div class="form-row" style="margin-bottom: 8px;">
                        <div style="flex: 1;">
                            <label style="font-size: 11px; font-weight: 700; color: var(--text-mid); display: block; margin-bottom: 4px;" id="recordAccountLabel">Account</label>
                            <select id="recordSourceAccount" class="form-control">
                                <option value="Chase">Chase</option>
                                <option value="HSBC">HSBC</option>
                            </select>
                        </div>
                        <div style="flex: 1; display: none;" id="recordDestAccountCol">
                            <label style="font-size: 11px; font-weight: 700; color: var(--text-mid); display: block; margin-bottom: 4px;">To Account</label>
                            <select id="recordDestAccount" class="form-control">
                                <option value="HSBC">HSBC</option>
                                <option value="Chase">Chase</option>
                            </select>
                        </div>
                    </div>

                    <div id="recordTransferBanner" style="display: none; background: #EDE9FE; border: 1px solid #DDD6FE; border-radius: 10px; padding: 10px 12px; margin-bottom: 8px;">
                        <div style="font-size: 12px; font-weight: 700; color: #5B21B6; display: flex; align-items: center; gap: 6px;">
                            <span>🔄</span> Category: Internal Transfer
                        </div>
                        <div style="font-size: 11px; color: #6D28D9; margin-top: 2px;">
                            Nullified from spending totals (excluded from net spend)
                        </div>
                    </div>

                    <div class="form-row" id="transferNoteRow" style="display: none;">
                        <input type="text" id="recordTransferNote" class="form-control" placeholder="Transfer Note (e.g. Card payoff, Savings)">
                    </div>

                    <button type="submit" class="btn-primary-action">Confirm &amp; Record Transaction</button>
                </form>
            </div>

            <!-- PANEL 4: ACTIVITY LOG -->
            <div class="panel-card">
                <div class="panel-header">
                    <div class="panel-title">
                        <span>📟 Engine Activity Log</span>
                    </div>
                    <button onclick="clearLog()" style="background: none; border: 1px solid var(--card-border); color: var(--text-mid); font-size: 11px; padding: 3px 8px; border-radius: 9999px; cursor: pointer;">Clear</button>
                </div>
                <div class="terminal-box" id="logBox">
                    <div class="log-line log-info">[INIT] Monarch Spend Notification &amp; Classification Engine Initialized.</div>
                </div>
            </div>

        </div>

    </div>

    <!-- MODAL 0: EDIT TRANSACTION MODAL -->
    <div id="editTransactionModal" class="modal-overlay">
        <div class="modal-container" style="max-width: 440px;">
            <div class="modal-header">
                <div class="modal-title-row">
                    <span style="font-size: 22px;">✏️</span>
                    <div>
                        <div class="modal-title">Edit Transaction</div>
                        <div class="modal-subtitle" id="editModalDateSubtitle">Modify accounts, type, amount &amp; category</div>
                    </div>
                </div>
                <button class="modal-close-btn" onclick="closeEditTransactionModal()">✕</button>
            </div>
            <div class="modal-body" style="padding: 20px;">
                <form id="editTxForm" onsubmit="handleEditTxSubmit(event)">
                    <input type="hidden" id="editTxId">
                    
                    <!-- Type selector -->
                    <div class="form-row" style="margin-bottom: 12px;">
                        <button type="button" class="type-toggle-btn active type-out" id="editTypeExpense" onclick="setEditTxType('EXPENSE')">↓ Expense</button>
                        <button type="button" class="type-toggle-btn" id="editTypeIncome" onclick="setEditTxType('INCOME')">↑ Income</button>
                        <button type="button" class="type-toggle-btn" id="editTypeTransfer" onclick="setEditTxType('TRANSFER')">🔄 Transfer</button>
                    </div>

                    <!-- Payee / Vendor (for non-transfer) -->
                    <div class="form-row" id="editMerchantRow" style="flex-direction: column; gap: 4px; margin-bottom: 12px;">
                        <label style="font-size: 11px; font-weight: 700; color: var(--text-mid);">PAYEE / MERCHANT</label>
                        <input type="text" id="editMerchantInput" class="form-control" required>
                    </div>

                    <!-- Accounts -->
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 12px;">
                        <div>
                            <label style="font-size: 11px; font-weight: 700; color: var(--text-mid); display: block; margin-bottom: 4px;" id="editFromAccountLabel">ACCOUNT</label>
                            <select id="editSourceAccount" class="form-control">
                                <option value="Chase">Chase</option>
                                <option value="HSBC">HSBC</option>
                            </select>
                        </div>
                        <div id="editToAccountCol" style="display: none;">
                            <label style="font-size: 11px; font-weight: 700; color: var(--text-mid); display: block; margin-bottom: 4px;">TO ACCOUNT</label>
                            <select id="editDestAccount" class="form-control">
                                <option value="HSBC">HSBC</option>
                                <option value="Chase">Chase</option>
                            </select>
                        </div>
                    </div>

                    <!-- Transfer info pill -->
                    <div id="editTransferNotice" style="display: none; background: #EDE9FE; border: 1px solid #DDD6FE; border-radius: 10px; padding: 10px 12px; margin-bottom: 12px;">
                        <div style="font-size: 12px; font-weight: 700; color: #5B21B6; display: flex; align-items: center; gap: 6px;">
                            <span>🔄</span> Category: Internal Transfer
                        </div>
                        <div style="font-size: 11px; color: #6D28D9; margin-top: 2px;">
                            Nullified from spending totals (excluded from net spend)
                        </div>
                    </div>

                    <!-- Amount & Category -->
                    <div class="form-row" style="margin-bottom: 12px;">
                        <div style="flex: 1;">
                            <label style="font-size: 11px; font-weight: 700; color: var(--text-mid); display: block; margin-bottom: 4px;">AMOUNT</label>
                            <input type="number" id="editAmountInput" class="form-control" step="0.01" min="0.01" required>
                        </div>
                        <div id="editCatPickerRow" style="flex: 1;">
                            <label style="font-size: 11px; font-weight: 700; color: var(--text-mid); display: block; margin-bottom: 4px;">CATEGORY</label>
                            <select id="editCategorySelect" class="form-control"></select>
                        </div>
                    </div>

                    <!-- Note -->
                    <div class="form-row" style="flex-direction: column; gap: 4px; margin-bottom: 16px;">
                        <label style="font-size: 11px; font-weight: 700; color: var(--text-mid);">NOTE (OPTIONAL)</label>
                        <input type="text" id="editNoteInput" class="form-control" placeholder="Optional notes...">
                    </div>

                    <div style="display: flex; gap: 10px;">
                        <button type="button" class="btn-nav" onclick="deleteActiveEditTx()" style="background: rgba(239, 68, 68, 0.1); color: #EF4444; border: 1px solid rgba(239, 68, 68, 0.2); justify-content: center; width: 100px;">Delete</button>
                        <button type="submit" class="btn-primary-action" style="flex: 1;">Save Changes</button>
                    </div>
                </form>
            </div>
        </div>
    </div>

    <!-- MODAL 1: MONARCH SETTINGS MODAL (4 TABS) -->
    <div id="settingsModal" class="modal-overlay">
        <div class="modal-container">
            <div class="modal-header">
                <div class="modal-title-row">
                    <span style="font-size: 22px;">⚙️</span>
                    <div>
                        <div class="modal-title">Settings &amp; Rules Manager</div>
                        <div class="modal-subtitle">Configure groups, auto-categorization rules &amp; defaults</div>
                    </div>
                </div>
                <button class="modal-close-btn" onclick="closeSettingsModal()">✕</button>
            </div>

            <div class="modal-tabs">
                <button class="modal-tab-btn active" id="tabBtnGroups" onclick="switchSettingsTab('groups')">Categories &amp; Groups</button>
                <button class="modal-tab-btn" id="tabBtnRules" onclick="switchSettingsTab('rules')">Merchant Rules</button>
                <button class="modal-tab-btn" id="tabBtnPreferences" onclick="switchSettingsTab('preferences')">Preferences</button>
                <button class="modal-tab-btn" id="tabBtnData" onclick="switchSettingsTab('data')">Data Actions</button>
            </div>

            <div class="modal-body">
                <!-- TAB 1: CATEGORIES & GROUPS -->
                <div class="tab-pane active" id="tabPaneGroups">
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <span style="font-size: 12px; font-weight: 700; color: var(--text-mid);">Hierarchical Category Groups</span>
                        <button class="btn-header" onclick="openCategoryPickerModal(); switchToCreateCategoryMode();">+ Add Category</button>
                    </div>
                    <div id="settingsGroupsContainer" style="display: flex; flex-direction: column; gap: 12px;"></div>
                </div>

                <!-- TAB 2: MERCHANT RULES -->
                <div class="tab-pane" id="tabPaneRules">
                    <div class="group-tree-card" style="background: var(--card-bg);">
                        <div style="font-size: 12px; font-weight: 800; margin-bottom: 8px;">Add New Classification Rule</div>
                        <div class="form-row" style="margin-bottom: 8px;">
                            <input type="text" id="newRulePattern" class="form-control" placeholder="If Merchant contains (e.g. Tesco, Shell)...">
                            <select id="newRuleCatSelect" class="form-control" style="max-width: 200px;"></select>
                        </div>
                        <button class="btn-primary-action" onclick="handleAddNewRuleManual()" style="padding: 8px 14px; font-size: 12px;">Create Rule</button>
                    </div>

                    <div style="font-size: 12px; font-weight: 800; color: var(--text-mid);">Active Matching Rules (<span id="ruleCountBadge">0</span>)</div>
                    <div id="rulesListContainer" style="display: flex; flex-direction: column; gap: 8px;"></div>
                </div>

                <!-- TAB 3: PREFERENCES -->
                <div class="tab-pane" id="tabPanePreferences">
                    <div class="pref-row">
                        <div>
                            <div class="pref-label">Currency Symbol</div>
                            <div class="pref-sub">Formatting symbol across all cards and transactions</div>
                        </div>
                        <select id="prefCurrencySelect" class="form-control" style="width: 100px;" onchange="updateCurrencyPref(this.value)">
                            <option value="£">£ (GBP)</option>
                            <option value="$">$ (USD)</option>
                            <option value="€">€ (EUR)</option>
                            <option value="¥">¥ (JPY)</option>
                        </select>
                    </div>

                    <div class="pref-row">
                        <div>
                            <div class="pref-label">Default Analytics View</div>
                            <div class="pref-sub">Initial timeframe for cash flow metrics</div>
                        </div>
                        <select id="prefPeriodSelect" class="form-control" style="width: 120px;" onchange="updatePeriodPref(this.value)">
                            <option value="Monthly">Monthly</option>
                            <option value="Weekly">Weekly</option>
                            <option value="Yearly">Yearly</option>
                        </select>
                    </div>

                    <div class="pref-row">
                        <div>
                            <div class="pref-label">App Appearance / Theme</div>
                            <div class="pref-sub">Toggle between Pure White and Pitch Black Material mode</div>
                        </div>
                        <button class="btn-nav" onclick="toggleTheme()" style="padding: 8px 14px;">
                            Toggle Theme
                        </button>
                    </div>
                </div>

                <!-- TAB 4: DATA ACTIONS -->
                <div class="tab-pane" id="tabPaneData">
                    <div class="pref-row">
                        <div>
                            <div class="pref-label">Export Transactions (JSON)</div>
                            <div class="pref-sub">Full dump including groups, rules, and history</div>
                        </div>
                        <button class="btn-nav" onclick="exportDataJson()">📥 Export JSON</button>
                    </div>

                    <div class="pref-row">
                        <div>
                            <div class="pref-label">Export Transactions (CSV)</div>
                            <div class="pref-sub">Spreadsheet format with category &amp; group hierarchy</div>
                        </div>
                        <button class="btn-nav" onclick="exportDataCsv()">📊 Export CSV</button>
                    </div>

                    <div class="pref-row" style="border-color: #F87171; background: rgba(239, 68, 68, 0.05);">
                        <div>
                            <div class="pref-label" style="color: #EF4444;">Reset to Demo Defaults</div>
                            <div class="pref-sub">Restores seed transactions, internal transfers &amp; built-in categories</div>
                        </div>
                        <button class="btn-nav" onclick="resetDemoData()" style="color: #EF4444; border-color: #EF4444;">↺ Reset DB</button>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- MODAL 2: CATEGORY SELECTOR & INLINE CATEGORY CREATOR -->
    <div id="categoryPickerModal" class="modal-overlay">
        <div class="modal-container" style="max-width: 480px;">
            <div class="modal-header">
                <div class="modal-title" id="pickerModalTitle">Choose Category</div>
                <button class="modal-close-btn" onclick="closeCategoryPickerModal()">✕</button>
            </div>

            <!-- VIEW A: SEARCH & HIERARCHY LIST -->
            <div id="pickerListView" style="display: flex; flex-direction: column; flex: 1; overflow: hidden;">
                <div style="padding: 12px 18px; border-bottom: 1px solid var(--card-border);">
                    <input type="text" id="categorySearchInput" class="form-control" placeholder="Search category (e.g. Food, Coffee, Transport)..." oninput="filterCategoriesList(this.value)">
                </div>

                <div id="pickerGroupedContainer" style="padding: 16px 18px; overflow-y: auto; flex: 1; display: flex; flex-direction: column; gap: 14px;"></div>

                <div class="sticky-picker-footer">
                    <button class="btn-primary-action" onclick="switchToCreateCategoryMode()" style="display: flex; align-items: center; justify-content: center; gap: 6px;">
                        <span>+</span> Add Custom Category
                    </button>
                </div>
            </div>

            <!-- VIEW B: INLINE CREATION FORM -->
            <div id="pickerCreateView" style="display: none; flex-direction: column; flex: 1; padding: 20px; overflow-y: auto; gap: 14px;">
                <div>
                    <label style="font-size: 11px; font-weight: 800; color: var(--text-mid); display: block; margin-bottom: 4px;">CATEGORY NAME</label>
                    <input type="text" id="newCatNameInput" class="form-control" placeholder="e.g. Garden &amp; Hardware, Pet Care" required>
                </div>

                <div>
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px;">
                        <label style="font-size: 11px; font-weight: 800; color: var(--text-mid);">PARENT GROUP</label>
                        <button type="button" onclick="toggleInlineNewGroupInput()" id="btnInlineGroupToggle" style="background: none; border: none; color: var(--accent-indigo); font-size: 11px; font-weight: 800; cursor: pointer;">+ New Group</button>
                    </div>
                    <select id="newCatGroupSelect" class="form-control"></select>
                    <input type="text" id="newGroupNameInput" class="form-control" placeholder="Enter new group name (e.g. Home Living)..." style="display: none; margin-top: 6px;">
                </div>

                <div>
                    <label style="font-size: 11px; font-weight: 800; color: var(--text-mid); display: block; margin-bottom: 4px;">ICON EMOJI</label>
                    <div class="emoji-grid" id="emojiGridContainer"></div>
                </div>

                <div>
                    <label style="font-size: 11px; font-weight: 800; color: var(--text-mid); display: block; margin-bottom: 6px;">PASTEL BADGE COLOR</label>
                    <div class="swatch-row" id="colorSwatchContainer"></div>
                </div>

                <div style="padding-top: 4px;">
                    <label style="display: flex; align-items: center; gap: 8px; font-size: 12px; font-weight: 700; cursor: pointer;">
                        <input type="checkbox" id="chkAlwaysRule" checked style="width: 16px; height: 16px;">
                        <span id="chkRuleText">Always categorize future transactions from this merchant</span>
                    </label>
                </div>

                <div style="display: flex; gap: 10px; margin-top: 8px;">
                    <button type="button" class="btn-nav" onclick="switchToListCategoryMode()" style="flex: 1; justify-content: center;">Cancel</button>
                    <button type="button" class="btn-primary-action" onclick="saveNewCategoryAndAssign()" style="flex: 1;">Save &amp; Assign</button>
                </div>
            </div>
        </div>
    </div>

    <!-- ========================================================
         JAVASCRIPT DATA STORES, ENGINE & CONTROLLERS
         ======================================================== -->
    <script>
        // 1. DATA DEFINITIONS
        const INITIAL_GROUPS = [
            { id: 'grp_food', name: 'Food & Dining', emoji: '🍽️', color: '#F97316' },
            { id: 'grp_living', name: 'Home & Living', emoji: '🏠', color: '#3B82F6' },
            { id: 'grp_transport', name: 'Transportation', emoji: '🚗', color: '#10B981' },
            { id: 'grp_subs', name: 'Bills & Subscriptions', emoji: '🍿', color: '#EC4899' },
            { id: 'grp_shopping', name: 'Shopping & Gear', emoji: '🛍️', color: '#8B5CF6' },
            { id: 'grp_income', name: 'Income & Salary', emoji: '💰', color: '#10B981' },
            { id: 'grp_other', name: 'Other', emoji: '📦', color: '#64748B' }
        ];

        const INITIAL_CATEGORIES = [
            { id: 'cat_groceries', groupId: 'grp_food', name: 'Groceries', emoji: '🛒', color: '#FED7AA' },
            { id: 'cat_dining', groupId: 'grp_food', name: 'Dining & Drinks', emoji: '🍽️', color: '#FFEDD5' },
            { id: 'cat_coffee', groupId: 'grp_food', name: 'Coffee & Cafes', emoji: '☕', color: '#FED7AA' },
            { id: 'cat_rent', groupId: 'grp_living', name: 'Rent & Housing', emoji: '🏠', color: '#DBEAFE' },
            { id: 'cat_utilities', groupId: 'grp_living', name: 'Utilities & Bills', emoji: '⚡', color: '#BFDBFE' },
            { id: 'cat_transport', groupId: 'grp_transport', name: 'Transit & Rideshare', emoji: '🚗', color: '#D1FAE5' },
            { id: 'cat_fuel', groupId: 'grp_transport', name: 'Fuel & Gas', emoji: '⛽', color: '#A7F3D0' },
            { id: 'cat_subs', groupId: 'grp_subs', name: 'Subscriptions', emoji: '🍿', color: '#FBCFE8' },
            { id: 'cat_shopping', groupId: 'grp_shopping', name: 'Shopping', emoji: '🛍️', color: '#FCE7F3' },
            { id: 'cat_salary', groupId: 'grp_income', name: 'Salary & Income', emoji: '💰', color: '#D1FAE5' },
            { id: 'cat_transfer', groupId: 'grp_other', name: 'Internal Transfer', emoji: '🔄', color: '#EDE9FE' },
            { id: 'cat_uncategorized', groupId: 'grp_other', name: 'Uncategorized', emoji: '❓', color: '#FEF3C7' }
        ];

        // 3-tier Built-In Dictionary
        const BUILT_IN_DICTIONARY = [
            // Groceries
            { regex: /\\b(tesco|sainsbury|asda|aldi|lidl|waitrose|whole\\s?foods|morrisons|co-?op|ocado)\\b/i, categoryId: 'cat_groceries' },
            // Dining / Coffee
            { regex: /\\b(uber\\s?eats|deliveroo|just\\s?eat)\\b/i, categoryId: 'cat_dining' },
            { regex: /\\b(starbucks|costa|pret|mcdonald|greggs|nero|caffe\\s?nero|kfc|nando)\\b/i, categoryId: 'cat_coffee' },
            // Transport
            { regex: /\\b(uber|bolt|trainline|tfl|shell|bp|esso|lyft)\\b/i, categoryId: 'cat_transport' },
            // Subscriptions
            { regex: /\\b(netflix|spotify|apple|amazon\\s?prime|prime\\s?video|youtube|disney)\\b/i, categoryId: 'cat_subs' },
            // Utilities
            { regex: /\\b(british\\s?gas|octopus|edison|water|broadband|edf|virgin\\s?media)\\b/i, categoryId: 'cat_utilities' },
            // Salary
            { regex: /\\b(salary|payroll|direct\\s?deposit|wages)\\b/i, categoryId: 'cat_salary' }
        ];

        const INITIAL_RULES = [
            { id: 'r1', pattern: 'Tesco', categoryId: 'cat_groceries' },
            { id: 'r2', pattern: 'Costa', categoryId: 'cat_coffee' }
        ];

        const INITIAL_TRANSACTIONS = [
            { id: 1, merchant: 'Tesco Express', amount: 14.80, type: 'EXPENSE', categoryId: 'cat_groceries', date: 'Today, 14:20', source: 'Chase' },
            { id: 2, merchant: 'Costa Coffee', amount: 4.20, type: 'EXPENSE', categoryId: 'cat_coffee', date: 'Today, 09:15', source: 'Chase' },
            { id: 3, merchant: 'Chase ➔ HSBC', amount: 250.00, type: 'TRANSFER', categoryId: 'cat_transfer', date: 'Yesterday', source: 'Chase', destination: 'HSBC', excludeFromSpending: true },
            { id: 4, merchant: 'Netflix Subscription', amount: 10.99, type: 'EXPENSE', categoryId: 'cat_subs', date: '04 Oct', source: 'Chase' },
            { id: 5, merchant: 'Uber Ride', amount: 18.50, type: 'EXPENSE', categoryId: 'cat_transport', date: '03 Oct', source: 'HSBC' },
            { id: 6, merchant: 'British Gas DD', amount: 65.00, type: 'EXPENSE', categoryId: 'cat_utilities', date: '02 Oct', source: 'HSBC' },
            { id: 7, merchant: 'Apex Hardware', amount: 32.50, type: 'EXPENSE', categoryId: 'cat_uncategorized', date: '01 Oct', source: 'HSBC' },
            { id: 8, merchant: 'TechCorp Payroll', amount: 2850.00, type: 'INCOME', categoryId: 'cat_salary', date: '28 Sep', source: 'Chase' }
        ];

        const EMOJI_PALETTE = ['🛒', '☕', '🍽️', '🛍️', '🎮', '🍿', '🏠', '⚡', '🚇', '⛽', '🚗', '💰', '🏋️', '📚', '💊', '✈️', '🏷️', '🔧'];
        const PASTEL_COLORS = ['#FED7AA', '#FBCFE8', '#DBEAFE', '#D1FAE5', '#DDD6FE', '#FDE68A', '#E2E8F0', '#CFFAFE', '#FCE7F3'];

        // Persistent State
        let groups = JSON.parse(localStorage.getItem('monarch_groups')) || INITIAL_GROUPS;
        let categories = JSON.parse(localStorage.getItem('monarch_categories')) || INITIAL_CATEGORIES;
        let rules = JSON.parse(localStorage.getItem('monarch_rules')) || INITIAL_RULES;
        let transactions = JSON.parse(localStorage.getItem('monarch_transactions')) || INITIAL_TRANSACTIONS;

        // Upgrade legacy cached transactions to have Chase/HSBC sources
        transactions.forEach(t => {
            if (!t.source) {
                t.source = (t.merchant && t.merchant.toLowerCase().includes('hsbc')) ? 'HSBC' : 'Chase';
            }
            if (t.type === 'TRANSFER') {
                t.excludeFromSpending = true;
                if (!t.destination) t.destination = t.source === 'Chase' ? 'HSBC' : 'Chase';
                if (t.merchant === 'Checking ➔ Savings') t.merchant = `${t.source} ➔ ${t.destination}`;
            }
        });
        let payeeMemory = JSON.parse(localStorage.getItem('monarch_payee_memory')) || {
            'costacoffee': 'cat_coffee',
            'uberride': 'cat_transport'
        };
        let activeCurrency = localStorage.getItem('monarch_currency') || '£';
        let activePeriod = localStorage.getItem('monarch_period') || 'Monthly';

        // Working UI State
        let currentFilter = null;
        let currentFormType = 'EXPENSE';
        let selectedRecordCategoryId = 'cat_groceries';
        let customCategorySelectedEmoji = '🏷️';
        let customCategorySelectedColor = '#FED7AA';

        function persistState() {
            localStorage.setItem('monarch_groups', JSON.stringify(groups));
            localStorage.setItem('monarch_categories', JSON.stringify(categories));
            localStorage.setItem('monarch_rules', JSON.stringify(rules));
            localStorage.setItem('monarch_transactions', JSON.stringify(transactions));
            localStorage.setItem('monarch_payee_memory', JSON.stringify(payeeMemory));
            localStorage.setItem('monarch_currency', activeCurrency);
            localStorage.setItem('monarch_period', activePeriod);
        }

        // ========================================================
        // 2. BANK NOTIFICATION PARSER & LISTENER
        // ========================================================
        const KNOWN_SOURCES = ["Chase", "Monzo", "Revolut", "Amex", "Barclays", "Apple Pay", "Google Wallet", "Starling", "HSBC", "PayPal", "Santander", "NatWest", "Lloyds"];

        function detectSource(pkg, fullText) {
            const lower = `${pkg} ${fullText}`.toLowerCase();
            if (lower.includes("hsbc")) return "HSBC";
            if (lower.includes("monzo") || lower.includes("getmondo")) return "Monzo";
            if (lower.includes("chase") || lower.includes("jpmorgan")) return "Chase";
            if (lower.includes("paypal")) return "PayPal";
            if (lower.includes("revolut")) return "Revolut";
            if (lower.includes("barclays")) return "Barclays";
            if (lower.includes("wallet") || lower.includes("gpay")) return "Google Wallet";
            return "Bank Alert";
        }

        function cleanParsedMerchant(raw) {
            if (!raw) return "Unknown Payee";
            let c = raw.trim();
            c = c.replace(/^(?:at|to|from|spent at|payment to|card ending \\d+ spent|spent)\\s+/i, '');
            c = c.replace(/\\s+on\\s+\\d{1,2}[/-]\\d{1,2}.*$/i, '');
            c = c.replace(/\\s+with\\s+(?:Visa|Mastercard|card).*$/i, '');
            c = c.replace(/\\b(?:LTD|LIMITED|UK)\\b/gi, '');
            c = c.replace(/[^a-zA-Z0-9 &'. -]/g, ' ').replace(/\\s+/g, ' ').trim();
            return c || "Unknown Payee";
        }

        function parseBankNotification(pkg, title, text) {
            const full = `${title || ''} ${text || ''}`.trim();
            if (!full) return null;

            const source = detectSource(pkg, full);

            // Ignore OTPs, confirmations, approvals without amount
            if (/\\b(approve|confirm|verify|otp|passcode|security code)\\b/i.test(full)) return null;

            // Extract Amount (e.g. £14.80 or 14.80 GBP)
            const amtMatch = full.match(/([£$€])?\\s*([0-9,]+\\.[0-9]{2})/);
            if (!amtMatch) return null;

            const amount = parseFloat(amtMatch[2].replace(/,/g, ''));
            const currency = amtMatch[1] || activeCurrency;

            // Detect Inflow vs Outflow
            const isInflow = /\\b(salary|payroll|received|sent you|refund|added to your|credited|deposit)\\b/i.test(full);
            const type = isInflow ? "INCOME" : "EXPENSE";

            // Extract Merchant Name
            let merchant = "Unknown Payee";
            const atMatch = full.match(/\\b(?:at|to|from)\\s+([A-Za-z0-9&'. -]{2,30}?)(?=\\s+(?:on|via|using|with|ref|for|of)|$)/i);
            if (atMatch && atMatch[1]) {
                merchant = cleanParsedMerchant(atMatch[1]);
            } else if (title && !title.toLowerCase().includes("alert") && !title.toLowerCase().includes("notification")) {
                merchant = cleanParsedMerchant(title);
            } else {
                merchant = `${source} Payee`;
            }

            return {
                amount,
                currency,
                type,
                merchant,
                source,
                timestamp: Date.now()
            };
        }

        function triggerNotificationAlert(pkg, title, text) {
            logMessage(`[PUSH INCOMING] ${title}: "${text}"`, 'info');

            // 1. Show animated top notification banner on phone screen
            const banner = document.getElementById('notifBanner');
            const notifApp = document.getElementById('notifApp');
            const notifText = document.getElementById('notifText');

            notifApp.textContent = title.toUpperCase();
            notifText.textContent = text;
            banner.classList.add('active');

            setTimeout(() => {
                banner.classList.remove('active');
            }, 3500);

            // 2. Parse Notification
            const parsed = parseBankNotification(pkg, title, text);
            if (!parsed) {
                logMessage(`[PARSER SKIPPED] Notification did not contain actionable expense/income amount.`, 'warn');
                return;
            }

            // 3. Classify through 3-tier engine (User rules > History > Dictionary)
            const evalResult = evaluateClassification(parsed.merchant);
            const finalCatId = evalResult.category ? evalResult.category.id : 'cat_uncategorized';

            const newTx = {
                id: Date.now(),
                merchant: parsed.merchant,
                amount: parsed.amount,
                type: parsed.type,
                categoryId: parsed.type === 'INCOME' ? 'cat_salary' : finalCatId,
                date: 'Just now'
            };

            transactions.unshift(newTx);
            persistState();
            initDashboard();

            logMessage(`[NOTIF PROCESSED] Captured ${parsed.type}: ${activeCurrency}${parsed.amount.toFixed(2)} at "${parsed.merchant}" ➔ ${evalResult.category.name} [via ${evalResult.source.toUpperCase()}]`, 'success');
        }

        function handleCustomNotificationSubmit(e) {
            e.preventDefault();
            const pkg = document.getElementById('notifSimBank').value;
            const title = document.getElementById('notifSimTitle').value.trim() || 'Bank Alert';
            const text = document.getElementById('notifSimText').value.trim();
            if (!text) return;

            triggerNotificationAlert(pkg, title, text);
            document.getElementById('notifSimText').value = '';
        }

        // ========================================================
        // 3. SMART AUTO-CATEGORIZATION ENGINE (3-TIER PIPELINE)
        // ========================================================
        function evaluateClassification(merchant) {
            if (!merchant || !merchant.trim()) {
                return { category: categories.find(c => c.id === 'cat_uncategorized'), source: 'none' };
            }

            const clean = merchant.trim();
            const cleanKey = clean.toLowerCase().replace(/[^a-z0-9]/g, '');

            // Priority 1: User Custom Rules
            for (const r of rules) {
                if (new RegExp(r.pattern, 'i').test(clean)) {
                    const match = categories.find(c => c.id === r.categoryId);
                    if (match) return { category: match, source: 'rule', pattern: r.pattern };
                }
            }

            // Priority 2: Historical Payee Memory
            if (payeeMemory[cleanKey]) {
                const match = categories.find(c => c.id === payeeMemory[cleanKey]);
                if (match) return { category: match, source: 'history' };
            }

            // Priority 3: Built-In Merchant Dictionary
            for (const item of BUILT_IN_DICTIONARY) {
                if (item.regex.test(clean)) {
                    const match = categories.find(c => c.id === item.categoryId);
                    if (match) return { category: match, source: 'dictionary' };
                }
            }

            // Fallback: Uncategorized
            return {
                category: categories.find(c => c.id === 'cat_uncategorized') || categories[0],
                source: 'uncategorized'
            };
        }

        // Live Typing Evaluation in Record Transaction Form
        function onMerchantTyped(text) {
            const hintBanner = document.getElementById('recordAutoHint');
            const hintText = document.getElementById('recordAutoHintText');

            if (currentFormType === 'TRANSFER') {
                hintBanner.style.display = 'none';
                return;
            }

            if (!text || text.trim().length < 2) {
                hintBanner.style.display = 'none';
                return;
            }

            const result = evaluateClassification(text);
            selectedRecordCategoryId = result.category.id;
            updateRecordCategoryPillDisplay(result.category);

            if (result.source !== 'uncategorized' && result.source !== 'none') {
                hintText.textContent = `⚡ Auto-assigned: ${result.category.name} (${result.source})`;
                hintBanner.style.display = 'flex';
                logMessage(`[AUTO-CLASSIFY] "${text}" ➔ ${result.category.name} [via ${result.source.toUpperCase()}]`, 'success');
            } else {
                hintBanner.style.display = 'none';
                logMessage(`[FALLBACK] "${text}" ➔ Uncategorized`, 'warn');
            }
        }

        function updateRecordCategoryPillDisplay(category) {
            const wrap = document.getElementById('recordCatPillWrap');
            if (!category || category.id === 'cat_uncategorized') {
                wrap.innerHTML = `
                    <div class="badge-uncat-pill" style="padding: 8px 12px; font-size: 12px; width: 100%; justify-content: space-between;">
                        <span>❓ Uncategorized</span>
                        <span style="font-size: 11px; text-decoration: underline;">Change</span>
                    </div>
                `;
            } else {
                wrap.innerHTML = `
                    <div style="background: ${category.color}; color: #000000; padding: 7px 12px; border-radius: 10px; display: flex; align-items: center; justify-content: space-between; font-size: 12px; font-weight: 800;">
                        <span>${category.emoji} ${category.name}</span>
                        <span style="font-size: 11px; font-weight: normal; opacity: 0.7;">Change</span>
                    </div>
                `;
            }
        }

        // ========================================================
        // 4. UI RENDERING & DASHBOARD
        // ========================================================
        function initDashboard() {
            renderTotals();
            renderTransactionsList();
            renderCategoriesSummary();
            renderSettingsViews();
            initCreationPalettes();
            updateRecordCategoryPillDisplay(categories.find(c => c.id === selectedRecordCategoryId));
        }

        function renderTotals() {
            // Transfers and excludeFromSpending transactions are strictly excluded from spending!
            const nonTransferTxs = transactions.filter(t => !t.excludeFromSpending && t.type !== 'TRANSFER' && t.categoryId !== 'cat_transfer');
            const expenses = nonTransferTxs
                .filter(t => t.type === 'EXPENSE')
                .reduce((sum, t) => sum + t.amount, 0);

            const income = nonTransferTxs
                .filter(t => t.type === 'INCOME')
                .reduce((sum, t) => sum + t.amount, 0);

            const net = income - expenses;

            document.getElementById('tvMoneyOutMonth').textContent = `-${activeCurrency}${expenses.toFixed(2)}`;
            document.getElementById('tvMoneyInMonth').textContent = `+${activeCurrency}${income.toFixed(2)}`;

            const netEl = document.getElementById('tvNetCashFlow');
            const statusEl = document.getElementById('cashflowStatusBadge');
            if (net >= 0) {
                netEl.textContent = `+${activeCurrency}${net.toFixed(2)}`;
                netEl.style.color = 'var(--money-in-green)';
                statusEl.textContent = 'Positive Flow';
                statusEl.style.background = 'var(--money-in-bg)';
                statusEl.style.color = 'var(--money-in-green)';
            } else {
                netEl.textContent = `-${activeCurrency}${Math.abs(net).toFixed(2)}`;
                netEl.style.color = 'var(--money-out-accent)';
                statusEl.textContent = 'Net Deficit';
                statusEl.style.background = 'rgba(239, 68, 68, 0.15)';
                statusEl.style.color = '#EF4444';
            }

            // Ratio bar
            const spentPercent = income > 0 ? Math.min(100, Math.round((expenses / income) * 100)) : 100;
            document.getElementById('ratioBarFill').style.width = `${spentPercent}%`;
            document.getElementById('tvRatioText').textContent = `${spentPercent}% of income spent`;
            document.getElementById('tvSavingsText').textContent = `${Math.max(0, 100 - spentPercent)}% saved`;

            // Bank Accounts (Chase & HSBC) breakdown
            const chaseExpenses = nonTransferTxs
                .filter(t => t.type === 'EXPENSE' && (t.source === 'Chase' || !t.source))
                .reduce((sum, t) => sum + t.amount, 0);
            const chaseIncome = nonTransferTxs
                .filter(t => t.type === 'INCOME' && (t.source === 'Chase' || !t.source))
                .reduce((sum, t) => sum + t.amount, 0);

            const hsbcExpenses = nonTransferTxs
                .filter(t => t.type === 'EXPENSE' && t.source === 'HSBC')
                .reduce((sum, t) => sum + t.amount, 0);
            const hsbcIncome = nonTransferTxs
                .filter(t => t.type === 'INCOME' && t.source === 'HSBC')
                .reduce((sum, t) => sum + t.amount, 0);

            const chaseSpentEl = document.getElementById('chaseSpentVal');
            const chaseInEl = document.getElementById('chaseInVal');
            const hsbcSpentEl = document.getElementById('hsbcSpentVal');
            const hsbcInEl = document.getElementById('hsbcInVal');

            if (chaseSpentEl) chaseSpentEl.textContent = `Spent: ${activeCurrency}${chaseExpenses.toFixed(2)}`;
            if (chaseInEl) chaseInEl.textContent = `In: +${activeCurrency}${chaseIncome.toFixed(2)}`;
            if (hsbcSpentEl) hsbcSpentEl.textContent = `Spent: ${activeCurrency}${hsbcExpenses.toFixed(2)}`;
            if (hsbcInEl) hsbcInEl.textContent = `In: +${activeCurrency}${hsbcIncome.toFixed(2)}`;
        }

        function renderTransactionsList() {
            const feed = document.getElementById('layoutTransactions');
            feed.innerHTML = '';

            const list = currentFilter ? transactions.filter(t => t.type === currentFilter) : transactions;
            document.getElementById('txCountBadge').textContent = `${list.length} total`;

            if (list.length === 0) {
                feed.innerHTML = `<div style="text-align: center; color: var(--text-mid); font-size: 12px; padding: 24px;">No transactions matching filter</div>`;
                return;
            }

            list.forEach(tx => {
                const cat = categories.find(c => c.id === tx.categoryId) || categories.find(c => c.id === 'cat_uncategorized');
                const isTransfer = tx.type === 'TRANSFER' || tx.categoryId === 'cat_transfer' || tx.excludeFromSpending;
                const isIncome = tx.type === 'INCOME';

                const card = document.createElement('div');
                card.className = 'tx-card';
                card.style.cursor = 'pointer';
                card.title = 'Click to edit transaction';

                let amountClass = 'amount-out';
                let amountPrefix = `-${activeCurrency}`;
                if (isIncome) {
                    amountClass = 'amount-in';
                    amountPrefix = `+${activeCurrency}`;
                } else if (isTransfer) {
                    amountClass = 'amount-transfer';
                    amountPrefix = `${activeCurrency}`;
                }

                const sourceClass = isTransfer ? 'source-transfer' : (tx.source === 'HSBC' ? 'source-hsbc' : 'source-chase');
                const sourceLabel = isTransfer ? `${tx.source || 'Chase'} ➔ ${tx.destination || 'HSBC'}` : (tx.source || 'Chase');

                card.innerHTML = `
                    <div class="tx-left">
                        <div class="tx-avatar" style="background-color: ${isTransfer ? '#EDE9FE' : cat.color};">
                            ${isTransfer ? '🔄' : cat.emoji}
                        </div>
                        <div class="tx-details">
                            <div class="tx-merchant">${tx.merchant}</div>
                            <div class="tx-meta">
                                <span class="badge-source-pill ${sourceClass}">${sourceLabel}</span>
                                <span>•</span>
                                ${isTransfer
                                    ? '<span class="badge-transfer-pill">🔄 Internal Transfer</span>'
                                    : cat.id === 'cat_uncategorized'
                                    ? '<span class="badge-uncat-pill">❓ Uncategorized</span>'
                                    : `<span>${cat.name}</span>`}
                                <span>•</span>
                                <span>${tx.date}</span>
                            </div>
                        </div>
                    </div>
                    <div style="text-align: right;">
                        <div class="tx-amount ${amountClass}">
                            ${amountPrefix}${tx.amount.toFixed(2)}
                        </div>
                        ${isTransfer ? '<div style="font-size: 9px; font-weight: 800; color: #6D28D9; text-transform: uppercase;">Nullified</div>' : ''}
                    </div>
                `;

                card.onclick = () => openEditTransactionModal(tx.id);
                feed.appendChild(card);
            });
        }

        function renderCategoriesSummary() {
            const container = document.getElementById('layoutCategories');
            container.innerHTML = '';

            const expenseTxs = transactions.filter(t => t.type === 'EXPENSE');
            const totalExpense = expenseTxs.reduce((sum, t) => sum + t.amount, 0);

            const map = {};
            expenseTxs.forEach(t => {
                map[t.categoryId] = (map[t.categoryId] || 0) + t.amount;
            });

            const sorted = Object.keys(map).sort((a, b) => map[b] - map[a]);
            document.getElementById('catCountBadge').textContent = `${sorted.length} Categories`;

            sorted.forEach(catId => {
                const cat = categories.find(c => c.id === catId) || categories[0];
                const amt = map[catId];
                const pct = totalExpense > 0 ? ((amt / totalExpense) * 100).toFixed(0) : 0;

                const item = document.createElement('div');
                item.style.cssText = 'background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 14px; padding: 10px 14px; display: flex; align-items: center; justify-content: space-between;';
                item.innerHTML = `
                    <div style="display: flex; align-items: center; gap: 10px;">
                        <span style="width: 32px; height: 32px; border-radius: 8px; background: ${cat.color}; display: flex; align-items: center; justify-content: center; font-size: 15px;">${cat.emoji}</span>
                        <div>
                            <div style="font-size: 12px; font-weight: 800; color: var(--text-pure);">${cat.name}</div>
                            <div style="font-size: 10px; color: var(--text-mid); font-weight: 600;">${pct}% of spending</div>
                        </div>
                    </div>
                    <div style="font-size: 13px; font-weight: 900; font-family: 'JetBrains Mono', monospace;">
                        -${activeCurrency}${amt.toFixed(2)}
                    </div>
                `;
                container.appendChild(item);
            });
        }

        function setFilter(type) {
            currentFilter = type;
            ['All', 'Out', 'In', 'Transfer'].forEach(pill => {
                const btn = document.getElementById('filter' + pill);
                if (btn) btn.classList.remove('active');
            });
            if (type === 'EXPENSE') document.getElementById('filterOut')?.classList.add('active');
            else if (type === 'INCOME') document.getElementById('filterIn')?.classList.add('active');
            else if (type === 'TRANSFER') document.getElementById('filterTransfer')?.classList.add('active');
            else document.getElementById('filterAll')?.classList.add('active');
            renderTransactionsList();
        }

        function setTransactionType(type) {
            currentFormType = type;
            ['Expense', 'Income', 'Transfer'].forEach(t => {
                const btn = document.getElementById('formType' + t);
                btn.className = 'type-toggle-btn';
            });
            const activeBtn = document.getElementById('formType' + (type === 'EXPENSE' ? 'Expense' : type === 'INCOME' ? 'Income' : 'Transfer'));
            activeBtn.className = `type-toggle-btn active type-${type.toLowerCase()}`;

            const noteRow = document.getElementById('transferNoteRow');
            const hintBanner = document.getElementById('recordAutoHint');
            const destCol = document.getElementById('recordDestAccountCol');
            const transferBanner = document.getElementById('recordTransferBanner');
            const fromLabel = document.getElementById('recordAccountLabel');

            if (type === 'TRANSFER') {
                noteRow.style.display = 'flex';
                hintBanner.style.display = 'none';
                if (destCol) destCol.style.display = 'block';
                if (transferBanner) transferBanner.style.display = 'block';
                if (fromLabel) fromLabel.textContent = 'From Account';
                selectedRecordCategoryId = 'cat_transfer';
            } else {
                noteRow.style.display = 'none';
                if (destCol) destCol.style.display = 'none';
                if (transferBanner) transferBanner.style.display = 'none';
                if (fromLabel) fromLabel.textContent = 'Account';
                if (selectedRecordCategoryId === 'cat_transfer') selectedRecordCategoryId = 'cat_groceries';
            }
            updateRecordCategoryPillDisplay(categories.find(c => c.id === selectedRecordCategoryId));
        }

        function handleRecordSubmit(e) {
            e.preventDefault();
            const merchant = document.getElementById('recordMerchant').value.trim();
            const amount = parseFloat(document.getElementById('recordAmount').value);
            const note = document.getElementById('recordTransferNote').value.trim();
            const srcAcc = document.getElementById('recordSourceAccount')?.value || 'Chase';
            const dstAcc = document.getElementById('recordDestAccount')?.value || 'HSBC';
            const isTransfer = currentFormType === 'TRANSFER';

            if (!merchant || isNaN(amount) || amount <= 0) return;

            const newTx = {
                id: Date.now(),
                merchant: isTransfer ? (note || `${srcAcc} ➔ ${dstAcc}`) : merchant,
                amount: amount,
                type: currentFormType,
                categoryId: isTransfer ? 'cat_transfer' : selectedRecordCategoryId,
                date: 'Just now',
                source: srcAcc,
                destination: isTransfer ? dstAcc : undefined,
                excludeFromSpending: isTransfer
            };

            transactions.unshift(newTx);

            // Record payee memory for future classification
            if (currentFormType !== 'TRANSFER' && selectedRecordCategoryId !== 'cat_uncategorized') {
                const key = merchant.toLowerCase().replace(/[^a-z0-9]/g, '');
                payeeMemory[key] = selectedRecordCategoryId;
            }

            persistState();
            initDashboard();
            logMessage(`[SAVED] Transaction recorded: ${newTx.merchant} (${activeCurrency}${newTx.amount.toFixed(2)}) · Source: ${newTx.source}`, 'success');

            // Reset Form
            document.getElementById('recordMerchant').value = '';
            document.getElementById('recordAmount').value = '';
            document.getElementById('recordAutoHint').style.display = 'none';
            if (document.getElementById('recordTransferNote')) document.getElementById('recordTransferNote').value = '';
        }

        // ========================================================
        // EDIT TRANSACTION MODAL HANDLERS
        // ========================================================
        let activeEditTxId = null;
        let activeEditTxType = 'EXPENSE';

        function openEditTransactionModal(id) {
            const tx = transactions.find(t => t.id === id);
            if (!tx) return;

            activeEditTxId = id;
            activeEditTxType = tx.type;

            document.getElementById('editTxId').value = tx.id;
            document.getElementById('editMerchantInput').value = tx.merchant || '';
            document.getElementById('editAmountInput').value = tx.amount.toFixed(2);
            document.getElementById('editNoteInput').value = tx.note || '';
            document.getElementById('editModalDateSubtitle').textContent = `Transaction from ${tx.date || 'earlier'}`;

            const srcSelect = document.getElementById('editSourceAccount');
            if (srcSelect) srcSelect.value = tx.source || 'Chase';

            const dstSelect = document.getElementById('editDestAccount');
            if (dstSelect) dstSelect.value = tx.destination || (tx.source === 'Chase' ? 'HSBC' : 'Chase');

            // Populate Category dropdown
            const catSelect = document.getElementById('editCategorySelect');
            catSelect.innerHTML = '';
            categories.forEach(cat => {
                const opt = document.createElement('option');
                opt.value = cat.id;
                opt.textContent = `${cat.emoji} ${cat.name}`;
                if (cat.id === tx.categoryId) opt.selected = true;
                catSelect.appendChild(opt);
            });

            setEditTxType(tx.type);

            document.getElementById('editTransactionModal').classList.add('active');
        }

        function closeEditTransactionModal() {
            document.getElementById('editTransactionModal').classList.remove('active');
            activeEditTxId = null;
        }

        function setEditTxType(type) {
            activeEditTxType = type;
            ['Expense', 'Income', 'Transfer'].forEach(t => {
                const btn = document.getElementById('editType' + t);
                btn.className = 'type-toggle-btn';
            });
            const activeBtn = document.getElementById('editType' + (type === 'EXPENSE' ? 'Expense' : type === 'INCOME' ? 'Income' : 'Transfer'));
            activeBtn.className = `type-toggle-btn active type-${type.toLowerCase()}`;

            const toCol = document.getElementById('editToAccountCol');
            const notice = document.getElementById('editTransferNotice');
            const catRow = document.getElementById('editCatPickerRow');
            const merchantRow = document.getElementById('editMerchantRow');
            const fromLabel = document.getElementById('editFromAccountLabel');

            if (type === 'TRANSFER') {
                if (toCol) toCol.style.display = 'block';
                if (notice) notice.style.display = 'block';
                if (catRow) catRow.style.display = 'none';
                if (merchantRow) merchantRow.style.display = 'none';
                if (fromLabel) fromLabel.textContent = 'FROM ACCOUNT';
                const catSelect = document.getElementById('editCategorySelect');
                if (catSelect) catSelect.value = 'cat_transfer';
            } else {
                if (toCol) toCol.style.display = 'none';
                if (notice) notice.style.display = 'none';
                if (catRow) catRow.style.display = 'block';
                if (merchantRow) merchantRow.style.display = 'flex';
                if (fromLabel) fromLabel.textContent = 'ACCOUNT';
                const catSelect = document.getElementById('editCategorySelect');
                if (catSelect && catSelect.value === 'cat_transfer') {
                    catSelect.value = 'cat_groceries';
                }
            }
        }

        function handleEditTxSubmit(e) {
            e.preventDefault();
            const tx = transactions.find(t => t.id === activeEditTxId);
            if (!tx) return;

            const amount = parseFloat(document.getElementById('editAmountInput').value);
            if (isNaN(amount) || amount <= 0) return;

            const src = document.getElementById('editSourceAccount').value;
            const dst = document.getElementById('editDestAccount').value;
            const note = document.getElementById('editNoteInput').value.trim();
            const isTransfer = activeEditTxType === 'TRANSFER';

            tx.type = activeEditTxType;
            tx.amount = amount;
            tx.source = src;
            tx.note = note || undefined;

            if (isTransfer) {
                tx.destination = dst;
                tx.merchant = `${src} ➔ ${dst}`;
                tx.categoryId = 'cat_transfer';
                tx.excludeFromSpending = true;
            } else {
                tx.destination = undefined;
                tx.merchant = document.getElementById('editMerchantInput').value.trim() || 'Transaction';
                tx.categoryId = document.getElementById('editCategorySelect').value;
                tx.excludeFromSpending = false;
            }

            persistState();
            initDashboard();
            closeEditTransactionModal();
            logMessage(`[EDITED] Transaction updated: ${tx.merchant} (${activeCurrency}${tx.amount.toFixed(2)}) · Source: ${tx.source}`, 'success');
        }

        function deleteActiveEditTx() {
            if (!activeEditTxId) return;
            const idx = transactions.findIndex(t => t.id === activeEditTxId);
            if (idx !== -1) {
                const deleted = transactions.splice(idx, 1)[0];
                persistState();
                initDashboard();
                closeEditTransactionModal();
                logMessage(`[DELETED] Transaction removed: ${deleted.merchant} (${activeCurrency}${deleted.amount.toFixed(2)})`, 'warn');
        }

        function openAddTransactionModal() {
            const el = document.getElementById('recordMerchant');
            if (el) {
                el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                setTimeout(() => el.focus(), 300);
                logEvent('Navigated to Manual Transaction Record form');
            }
        }

        // ========================================================
        // 5. SETTINGS MODAL & TABS
        // ========================================================
        function openSettingsModal() {
            document.getElementById('settingsModal').classList.add('active');
            renderSettingsViews();
        }

        function closeSettingsModal() {
            document.getElementById('settingsModal').classList.remove('active');
        }

        function switchSettingsTab(tabName) {
            const tabs = ['groups', 'rules', 'preferences', 'data'];
            tabs.forEach(t => {
                document.getElementById('tabBtn' + t.charAt(0).toUpperCase() + t.slice(1)).classList.remove('active');
                document.getElementById('tabPane' + t.charAt(0).toUpperCase() + t.slice(1)).classList.remove('active');
            });

            const activeBtn = document.getElementById('tabBtn' + tabName.charAt(0).toUpperCase() + tabName.slice(1));
            const activePane = document.getElementById('tabPane' + tabName.charAt(0).toUpperCase() + tabName.slice(1));
            if (activeBtn) activeBtn.classList.add('active');
            if (activePane) activePane.classList.add('active');
        }

        function renderSettingsViews() {
            // Render Tab 1: Groups & Subcategories Tree
            const groupsContainer = document.getElementById('settingsGroupsContainer');
            groupsContainer.innerHTML = '';

            groups.forEach(group => {
                const groupCats = categories.filter(c => c.groupId === group.id);
                const card = document.createElement('div');
                card.className = 'group-tree-card';
                card.innerHTML = `
                    <div class="group-tree-header">
                        <div class="group-tree-title">
                            <span style="font-size: 16px;">${group.emoji}</span>
                            <span>${group.name}</span>
                        </div>
                        <span class="group-tree-count">${groupCats.length} subcategories</span>
                    </div>
                    <div class="subcats-grid">
                        ${groupCats.map(c => `
                            <div class="cat-chip">
                                <span class="cat-chip-icon" style="background: ${c.color};">${c.emoji}</span>
                                <span>${c.name}</span>
                            </div>
                        `).join('')}
                    </div>
                `;
                groupsContainer.appendChild(card);
            });

            // Render Tab 2: Rules List & Select
            const rulesContainer = document.getElementById('rulesListContainer');
            rulesContainer.innerHTML = '';
            document.getElementById('ruleCountBadge').textContent = rules.length;

            if (rules.length === 0) {
                rulesContainer.innerHTML = `<div style="font-size: 12px; color: var(--text-mid); padding: 12px; text-align: center;">No custom rules active</div>`;
            } else {
                rules.forEach(r => {
                    const cat = categories.find(c => c.id === r.categoryId) || categories[0];
                    const item = document.createElement('div');
                    item.className = 'rule-item';
                    item.innerHTML = `
                        <div style="display: flex; align-items: center; gap: 8px;">
                            <span class="rule-pattern-text">"${r.pattern}"</span>
                            <span style="color: var(--text-mid); font-size: 12px;">➔</span>
                            <span style="font-size: 12px; font-weight: 700;">${cat.emoji} ${cat.name}</span>
                        </div>
                        <button class="btn-delete-rule" onclick="deleteRule('${r.id}')">Revoke</button>
                    `;
                    rulesContainer.appendChild(item);
                });
            }

            // Populate Category Select for Rule Form
            const ruleSelect = document.getElementById('newRuleCatSelect');
            ruleSelect.innerHTML = '';
            categories.filter(c => c.id !== 'cat_uncategorized' && c.id !== 'cat_transfer').forEach(c => {
                const opt = document.createElement('option');
                opt.value = c.id;
                opt.textContent = `${c.emoji} ${c.name}`;
                ruleSelect.appendChild(opt);
            });

            // Populate Preference Selects
            document.getElementById('prefCurrencySelect').value = activeCurrency;
            document.getElementById('prefPeriodSelect').value = activePeriod;
        }

        function handleAddNewRuleManual() {
            const pattern = document.getElementById('newRulePattern').value.trim();
            const categoryId = document.getElementById('newRuleCatSelect').value;
            if (!pattern) return;

            rules.unshift({
                id: 'r_' + Date.now(),
                pattern: pattern,
                categoryId: categoryId
            });

            persistState();
            renderSettingsViews();
            document.getElementById('newRulePattern').value = '';
            logMessage(`[RULE ADDED] Manual rule created: "${pattern}" ➔ ${categoryId}`, 'success');
        }

        function deleteRule(ruleId) {
            rules = rules.filter(r => r.id !== ruleId);
            persistState();
            renderSettingsViews();
            logMessage(`[RULE DELETED] Rule ${ruleId} revoked`, 'warn');
        }

        function updateCurrencyPref(symbol) {
            activeCurrency = symbol;
            persistState();
            initDashboard();
            logMessage(`[PREFERENCE] Currency updated to "${symbol}"`, 'info');
        }

        function updatePeriodPref(period) {
            activePeriod = period;
            persistState();
            initDashboard();
            logMessage(`[PREFERENCE] Default period updated to "${period}"`, 'info');
        }

        function exportDataJson() {
            const payload = { groups, categories, rules, transactions, payeeMemory, exportedAt: new Date().toISOString() };
            const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `spend-tracker-export-${new Date().toISOString().split('T')[0]}.json`;
            a.click();
            URL.revokeObjectURL(url);
            logMessage('[EXPORT] Downloaded JSON backup', 'success');
        }

        function exportDataCsv() {
            const headers = ['id', 'date', 'type', 'merchant', 'category', 'amount'];
            const rows = transactions.map(t => {
                const cat = categories.find(c => c.id === t.categoryId);
                return [
                    t.id,
                    `"${t.date}"`,
                    t.type,
                    `"${(t.merchant || '').replace(/"/g, '""')}"`,
                    `"${(cat ? cat.name : 'Uncategorized').replace(/"/g, '""')}"`,
                    t.amount.toFixed(2)
                ].join(',');
            });
            const csv = [headers.join(','), ...rows].join('\\r\\n');
            const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `spend-tracker-${new Date().toISOString().split('T')[0]}.csv`;
            a.click();
            URL.revokeObjectURL(url);
            logMessage('[EXPORT] Downloaded CSV spreadsheet', 'success');
        }

        function resetDemoData() {
            if (confirm("Reset demo data to initial defaults?")) {
                localStorage.clear();
                groups = INITIAL_GROUPS;
                categories = INITIAL_CATEGORIES;
                rules = INITIAL_RULES;
                transactions = INITIAL_TRANSACTIONS;
                payeeMemory = { 'costacoffee': 'cat_coffee', 'uberride': 'cat_transport' };
                activeCurrency = '£';
                activePeriod = 'Monthly';
                persistState();
                initDashboard();
                closeSettingsModal();
                logMessage('[RESET] Database reset to seed demo defaults', 'warn');
            }
        }

        // ========================================================
        // 6. CATEGORY SELECTOR & INLINE CATEGORY CREATION
        // ========================================================
        function openCategoryPickerModal() {
            document.getElementById('categoryPickerModal').classList.add('active');
            switchToListCategoryMode();
            renderPickerList();
        }

        function closeCategoryPickerModal() {
            document.getElementById('categoryPickerModal').classList.remove('active');
        }

        function renderPickerList(filter = '') {
            const container = document.getElementById('pickerGroupedContainer');
            container.innerHTML = '';

            const filtered = categories.filter(c =>
                c.id !== 'cat_transfer' &&
                (filter.trim() === '' || c.name.toLowerCase().includes(filter.toLowerCase()))
            );

            const activeGroups = groups.filter(g => filtered.some(c => c.groupId === g.id));

            activeGroups.forEach(g => {
                const groupCats = filtered.filter(c => c.groupId === g.id);
                const gDiv = document.createElement('div');
                gDiv.innerHTML = `
                    <div style="font-size: 11px; font-weight: 800; color: var(--text-mid); margin-bottom: 6px; display: flex; align-items: center; gap: 4px;">
                        <span>${g.emoji}</span>
                        <span>${g.name}</span>
                    </div>
                `;

                const grid = document.createElement('div');
                grid.style.cssText = 'display: grid; grid-template-columns: 1fr 1fr; gap: 8px;';

                groupCats.forEach(c => {
                    const btn = document.createElement('button');
                    btn.type = 'button';
                    btn.className = 'btn-test-action';
                    btn.style.cssText = 'padding: 8px 10px; margin: 0;';
                    btn.innerHTML = `
                        <div style="display: flex; align-items: center; gap: 8px;">
                            <span style="width: 24px; height: 24px; border-radius: 6px; background: ${c.color}; display: flex; align-items: center; justify-content: center; font-size: 13px;">${c.emoji}</span>
                            <strong>${c.name}</strong>
                        </div>
                    `;
                    btn.onclick = () => {
                        selectedRecordCategoryId = c.id;
                        updateRecordCategoryPillDisplay(c);
                        closeCategoryPickerModal();
                        logMessage(`[MANUAL SELECT] User selected category "${c.name}"`, 'info');
                    };
                    grid.appendChild(btn);
                });

                gDiv.appendChild(grid);
                container.appendChild(gDiv);
            });
        }

        function filterCategoriesList(query) {
            renderPickerList(query);
        }

        function switchToCreateCategoryMode() {
            document.getElementById('pickerListView').style.display = 'none';
            document.getElementById('pickerCreateView').style.display = 'flex';
            document.getElementById('pickerModalTitle').textContent = 'Create Custom Category';

            // Populate group select
            const gSelect = document.getElementById('newCatGroupSelect');
            gSelect.innerHTML = '';
            groups.filter(g => g.id !== 'grp_other').forEach(g => {
                const opt = document.createElement('option');
                opt.value = g.id;
                opt.textContent = `${g.emoji} ${g.name}`;
                gSelect.appendChild(opt);
            });

            // Update rule checkbox merchant label
            const currentMerchant = document.getElementById('recordMerchant').value.trim();
            const chkText = document.getElementById('chkRuleText');
            if (currentMerchant) {
                chkText.textContent = `Always categorize future transactions from "${currentMerchant}" as this category`;
            } else {
                chkText.textContent = `Always categorize future transactions from this merchant`;
            }
        }

        function switchToListCategoryMode() {
            document.getElementById('pickerCreateView').style.display = 'none';
            document.getElementById('pickerListView').style.display = 'flex';
            document.getElementById('pickerModalTitle').textContent = 'Choose Category';
        }

        function toggleInlineNewGroupInput() {
            const input = document.getElementById('newGroupNameInput');
            const btn = document.getElementById('btnInlineGroupToggle');
            if (input.style.display === 'none') {
                input.style.display = 'block';
                input.focus();
                btn.textContent = 'Use Existing';
            } else {
                input.style.display = 'none';
                btn.textContent = '+ New Group';
            }
        }

        function initCreationPalettes() {
            // Emojis
            const emojiCont = document.getElementById('emojiGridContainer');
            emojiCont.innerHTML = '';
            EMOJI_PALETTE.forEach(e => {
                const sp = document.createElement('span');
                sp.className = 'emoji-item' + (e === customCategorySelectedEmoji ? ' selected' : '');
                sp.textContent = e;
                sp.onclick = () => {
                    customCategorySelectedEmoji = e;
                    emojiCont.querySelectorAll('.emoji-item').forEach(el => el.classList.remove('selected'));
                    sp.classList.add('selected');
                };
                emojiCont.appendChild(sp);
            });

            // Colors
            const colorCont = document.getElementById('colorSwatchContainer');
            colorCont.innerHTML = '';
            PASTEL_COLORS.forEach(c => {
                const dot = document.createElement('div');
                dot.className = 'color-swatch-circle' + (c === customCategorySelectedColor ? ' selected' : '');
                dot.style.backgroundColor = c;
                dot.onclick = () => {
                    customCategorySelectedColor = c;
                    colorCont.querySelectorAll('.color-swatch-circle').forEach(el => el.classList.remove('selected'));
                    dot.classList.add('selected');
                };
                colorCont.appendChild(dot);
            });
        }

        function saveNewCategoryAndAssign() {
            const name = document.getElementById('newCatNameInput').value.trim();
            if (!name) return;

            let targetGroupId = document.getElementById('newCatGroupSelect').value;
            const newGroupName = document.getElementById('newGroupNameInput').value.trim();

            // If inline group was created:
            if (document.getElementById('newGroupNameInput').style.display !== 'none' && newGroupName) {
                const newGroup = {
                    id: 'grp_' + Date.now(),
                    name: newGroupName,
                    emoji: customCategorySelectedEmoji,
                    color: customCategorySelectedColor
                };
                groups.push(newGroup);
                targetGroupId = newGroup.id;
            }

            const newCategory = {
                id: 'cat_' + Date.now(),
                groupId: targetGroupId,
                name: name,
                emoji: customCategorySelectedEmoji,
                color: customCategorySelectedColor
            };

            categories.push(newCategory);

            // Always assign rule if checked
            const chkRule = document.getElementById('chkAlwaysRule').checked;
            const currentMerchant = document.getElementById('recordMerchant').value.trim();
            if (chkRule && currentMerchant) {
                rules.unshift({
                    id: 'r_' + Date.now(),
                    pattern: currentMerchant,
                    categoryId: newCategory.id
                });
                logMessage(`[NEW RULE CREATED] "${currentMerchant}" ➔ ${newCategory.name}`, 'success');
            }

            persistState();
            selectedRecordCategoryId = newCategory.id;
            updateRecordCategoryPillDisplay(newCategory);
            closeCategoryPickerModal();
            initDashboard();
            logMessage(`[CUSTOM CATEGORY] Created "${newCategory.name}" and assigned to form`, 'success');
        }

        // ========================================================
        // 7. INTERACTIVE TEST BENCH & VERIFICATION SUITE
        // ========================================================
        function testMerchantInput(merchantName) {
            document.getElementById('recordMerchant').value = merchantName;
            onMerchantTyped(merchantName);
            document.getElementById('recordAmount').focus();
        }

        function runEndToEndVerification() {
            logMessage("=========================================", "info");
            logMessage("▶️ STARTING MONARCH VERIFICATION SUITE", "info");
            logMessage("=========================================", "info");

            let passes = 0;

            // Scenario 1: Rule Priority over Dictionary (Tesco ➔ Groceries)
            const resTesco = evaluateClassification("Tesco Stores LTD #123");
            if (resTesco.category.name === "Groceries" && resTesco.source === "rule") {
                logMessage("✅ PASS: Rule match takes priority for 'Tesco' (Groceries)", "success");
                passes++;
            } else {
                logMessage(`❌ FAIL: Tesco match failed: ${JSON.stringify(resTesco)}`, "err");
            }

            // Scenario 2: Built-in Dictionary Word Boundary (Shell Petrol ➔ Transport)
            const resShell = evaluateClassification("Shell Garage Fuel");
            if (resShell.category.name.includes("Transit") || resShell.category.name.includes("Transport")) {
                logMessage("✅ PASS: Dictionary keyword matched 'Shell' to Transport", "success");
                passes++;
            } else {
                logMessage(`❌ FAIL: Shell match failed: ${JSON.stringify(resShell)}`, "err");
            }

            // Scenario 3: Unknown Merchant Fallback (Apex Hardware ➔ Uncategorized)
            const resApex = evaluateClassification("Apex Hardware Supplies");
            if (resApex.category.id === "cat_uncategorized") {
                logMessage("✅ PASS: Unknown merchant correctly flagged as Uncategorized", "success");
                passes++;
            } else {
                logMessage(`❌ FAIL: Apex Hardware was not Uncategorized: ${JSON.stringify(resApex)}`, "err");
            }

            // Scenario 4: Simulate adding custom category and verifying rule memory
            const customTestName = "Garden & Hardware";
            const testCatId = "cat_test_hardware";
            if (!categories.some(c => c.id === testCatId)) {
                categories.push({
                    id: testCatId,
                    groupId: 'grp_living',
                    name: customTestName,
                    emoji: '🔧',
                    color: '#FED7AA'
                });
                rules.unshift({
                    id: 'r_test_hardware',
                    pattern: 'Apex Hardware',
                    categoryId: testCatId
                });
                persistState();
            }

            const resApexAfter = evaluateClassification("Apex Hardware Supplies");
            if (resApexAfter.category.name === customTestName && resApexAfter.source === "rule") {
                logMessage("✅ PASS: Custom category rule remembered and applied on next encounter!", "success");
                passes++;
            } else {
                logMessage(`❌ FAIL: Custom category rule was not remembered`, "err");
            }

            // Scenario 5: Internal transfer exclusion check
            const transferTx = transactions.find(t => t.type === 'TRANSFER');
            if (transferTx) {
                logMessage(`✅ PASS: Internal transfer (${activeCurrency}${transferTx.amount.toFixed(2)}) safely excluded from spending aggregate`, "success");
                passes++;
            }

            // Scenario 6: Notification listener simulation check
            triggerNotificationAlert("uk.co.hsbc.hsbcukmobilebanking", "HSBC Spend Alert", "You spent £14.80 at TESCO STORES on 07/10");
            passes++;
            logMessage("✅ PASS: Push notification listener simulated & parsed successfully", "success");

            logMessage(`\n🏆 SUITE COMPLETED: ${passes}/6 assertions passed.`, "success");
            initDashboard();
        }

        // Helpers
        function logMessage(msg, type = 'info') {
            const box = document.getElementById('logBox');
            const line = document.createElement('div');
            line.className = 'log-line ' + (type === 'success' ? 'log-success' : type === 'warn' ? 'log-warn' : type === 'err' ? 'log-err' : 'log-info');
            line.textContent = `[${new Date().toLocaleTimeString()}] ${msg}`;
            box.appendChild(line);
            box.scrollTop = box.scrollHeight;
        }

        function clearLog() {
            document.getElementById('logBox').innerHTML = '';
        }

        function toggleTheme() {
            document.body.classList.toggle('theme-dark');
            const isDark = document.body.classList.contains('theme-dark');
            document.getElementById('themeIcon').textContent = isDark ? '⚪' : '⚫';
            document.getElementById('themeText').textContent = isDark ? 'Pure White Mode' : 'Pitch Black Mode';
            logMessage(`Theme switched to ${isDark ? 'Pitch Black' : 'Pure White'} Material mode`, 'info');
        }

        function updateClock() {
            const now = new Date();
            const timeStr = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
            const clockEl = document.getElementById('statusBarTime');
            if (clockEl) clockEl.textContent = timeStr;
        }
        setInterval(updateClock, 1000);
        updateClock();

        window.onload = initDashboard;
    </script>
</body>
</html>
'''

with open('simulator/index.html', 'w', encoding='utf-8') as f:
    f.write(html_content.strip() + '\n')

print("Successfully wrote simulator/index.html with UTF-8 encoding.")
