/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2019 The University of Queensland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.edu.uq.rcc.nimrodg.debug.agent;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class LogPanel extends JPanel implements ILogger {

	private final _Model m_Model;

	private final ImageIcon m_InfoIcon;
	private final ImageIcon m_WarnIcon;
	private final ImageIcon m_ErrIcon;

	private static final Component COMPONENT = new Component() {
	};

	private static ImageIcon extractAndScaleIcon(Icon icon) {
		BufferedImage img = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics g = img.getGraphics();
		icon.paintIcon(COMPONENT, g, 0, 0);
		return new ImageIcon(img.getScaledInstance(icon.getIconWidth() / 2, icon.getIconHeight() / 2, Image.SCALE_SMOOTH));
	}

	public LogPanel() {
		initComponents();
		m_Model = new _Model();
		m_List.setModel(m_Model);
		m_List.setCellRenderer(new _CellRenderer());

		m_InfoIcon = extractAndScaleIcon(UIManager.getIcon("OptionPane.informationIcon"));
		m_WarnIcon = extractAndScaleIcon(UIManager.getIcon("OptionPane.warningIcon"));
		m_ErrIcon = extractAndScaleIcon(UIManager.getIcon("OptionPane.errorIcon"));
	}

	@SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        m_List = new javax.swing.JList<>();

        jScrollPane1.setViewportView(m_List);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

	@Override
	public void log(Level level, String fmt, Object... args) {
		SwingUtilities.invokeLater(() -> {
			m_Model.addElement(new LogEntry(level, String.format(fmt, args)));
			int last = m_Model.getSize() - 1;
			m_List.setSelectedIndex(last);
			m_List.ensureIndexIsVisible(last);
		});
	}

	@Override
	public void log(Level level, Throwable e) {
		log(level, "Caught Exception: %s", e.getMessage());
	}

	@Override
	public void clear() {
		SwingUtilities.invokeLater(() -> {
			m_Model.clear();
		});
	}

	public static final DateTimeFormatter FORMATTER
			= DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss", Locale.ENGLISH)
					.withZone(ZoneId.systemDefault());

	private class LogEntry {

		public LogEntry(Level level, String msg) {
			this.level = level;
			this.message = msg;
			this.timestamp = Instant.now(Clock.systemUTC());
		}

		public final Instant timestamp;
		public final Level level;
		public final String message;

		@Override
		public String toString() {
			return String.format("%20s %s", FORMATTER.format(timestamp), message);
		}
	}

	private class _Model extends DefaultListModel<LogEntry> {

	}

	private class _CellRenderer implements ListCellRenderer<LogEntry> {

		private final DefaultListCellRenderer m_Renderer;

		public _CellRenderer() {
			m_Renderer = new DefaultListCellRenderer();
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends LogEntry> list, LogEntry value, int index, boolean isSelected, boolean cellHasFocus) {
			JLabel l = (JLabel) m_Renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

			switch(value.level) {
				case ERR:
					l.setIcon(m_ErrIcon);
					break;
				case WARN:
					l.setIcon(m_WarnIcon);
					break;
				case INFO:
					l.setIcon(m_InfoIcon);
					break;
			}
			return l;
		}

	}

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JList<LogEntry> m_List;
    // End of variables declaration//GEN-END:variables

}
