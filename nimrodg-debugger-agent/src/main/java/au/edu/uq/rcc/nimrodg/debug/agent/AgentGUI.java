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

import au.edu.uq.rcc.nimrodg.master.sig.SigUtils;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.DefaultComboBoxModel;

public class AgentGUI extends javax.swing.JFrame {

	private final ActionListener m_Listener;
	private final DefaultComboBoxModel<String> m_SigModel;

	public AgentGUI() {
		this((ActionEvent e) -> {
		});
	}

	public AgentGUI(ActionListener listener) {
		m_Listener = listener;
		initComponents();
		this.addWindowListener(new _WindowAdapter());
		m_SigModel = new DefaultComboBoxModel<>(SigUtils.ALGORITHMS.keySet().stream()
                        .sorted().toArray(String[]::new));
		m_SigModel.setSelectedItem(SigUtils.DEFAULT_ALGORITHM);
		m_SigningAlgorithm.setModel(m_SigModel);
	}

	public void setConnectProgress(float perc) {
		m_ConnectProgressBar.setValue((int)(perc * 100));
	}

	public void setConnectStatus(String status, Color colour) {
		m_ConnectProgressBar.setString(status);
		//m_ConnectProgressBar.setForeground(colour);
	}

	public AgentControlPanel getAgentPanel() {
		return m_AgentPanel;
	}

	public ILogger getLogger() {
		return m_LogPanel;
	}

	public AgentStatusPanel getStatusPanel() {
		return m_StatusPanel;
	}

	public enum ConnectionPanelState {
		CONNECT,
		DISCONNECT,
		LOCKED
	}

	public void setConnectionPanelState(ConnectionPanelState state) {
		if(state == ConnectionPanelState.CONNECT) {
			m_ConnectBtn.setText("Connect");
			m_ConnectBtn.setEnabled(true);
			m_AMQPUrl.setEditable(true);
			m_RoutingKey.setEditable(true);
		} else if(state == ConnectionPanelState.DISCONNECT) {
			m_ConnectBtn.setText("Disconnect");
			m_ConnectBtn.setEnabled(true);
			m_AMQPUrl.setEditable(false);
			m_RoutingKey.setEditable(false);
		} else {
			assert state == ConnectionPanelState.LOCKED;
			m_ConnectBtn.setEnabled(false);
			m_AMQPUrl.setEditable(false);
			m_RoutingKey.setEditable(false);
		}
	}

	public String getAMQPUri() {
		return m_AMQPUrl.getText();
	}

	public String getRoutingKey() {
		return m_RoutingKey.getText();
	}

	public String getTransferURI() {
		return m_TransferUri.getText();
	}

	public String getSigningAlgorithm() {
		return (String)m_SigningAlgorithm.getSelectedItem();
	}

	public String getSecretKey() {
		return m_SecretKey.getText();
	}

	public boolean getShowMessageWindow() {
		return m_ShowMessageWindow.isSelected();
	}

	@SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        javax.swing.JLabel jLabel3 = new javax.swing.JLabel();
        m_AMQPUrl = new javax.swing.JTextField();
        m_ConnectBtn = new javax.swing.JButton();
        javax.swing.JLabel jLabel4 = new javax.swing.JLabel();
        m_TransferUri = new javax.swing.JTextField();
        javax.swing.JLabel jLabel7 = new javax.swing.JLabel();
        javax.swing.JLabel jLabel5 = new javax.swing.JLabel();
        m_RoutingKey = new javax.swing.JTextField();
        javax.swing.JLabel jLabel6 = new javax.swing.JLabel();
        m_SecretKey = new javax.swing.JTextField();
        m_SigningAlgorithm = new javax.swing.JComboBox<>();
        javax.swing.JSplitPane jSplitPane1 = new javax.swing.JSplitPane();
        jPanel4 = new javax.swing.JPanel();
        m_AgentPanel = new au.edu.uq.rcc.nimrodg.debug.agent.AgentControlPanel();
        m_StatusPanel = new au.edu.uq.rcc.nimrodg.debug.agent.AgentStatusPanel();
        m_LogPanel = new au.edu.uq.rcc.nimrodg.debug.agent.LogPanel();
        jPanel2 = new javax.swing.JPanel();
        m_ConnectProgressBar = new javax.swing.JProgressBar();
        jMenuBar1 = new javax.swing.JMenuBar();
        javax.swing.JMenu jMenu1 = new javax.swing.JMenu();
        javax.swing.JMenuItem jMenuItem2 = new javax.swing.JMenuItem();
        javax.swing.JPopupMenu.Separator jSeparator2 = new javax.swing.JPopupMenu.Separator();
        javax.swing.JMenuItem jMenuItem3 = new javax.swing.JMenuItem();
        jMenu2 = new javax.swing.JMenu();
        m_ShowMessageWindow = new javax.swing.JCheckBoxMenuItem();

        setTitle("Nimrod/G Agent Debugger");

        jLabel3.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        jLabel3.setText("AMQP URI");
        jLabel3.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);

        m_AMQPUrl.setText("amqp://guest:guest@127.0.0.1/nimrod");

        m_ConnectBtn.setText("Connect");
        m_ConnectBtn.setActionCommand("connect");
        m_ConnectBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                onActionPerformed(evt);
            }
        });

        jLabel4.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        jLabel4.setText("Transfer URI");
        jLabel4.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);

        m_TransferUri.setText("file:///home/zane/Desktop/agentwork");

        jLabel7.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        jLabel7.setText("Algorithm");
        jLabel7.setToolTipText("");
        jLabel7.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);

        jLabel5.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        jLabel5.setText("Routing Key");
        jLabel5.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);

        m_RoutingKey.setText("iamthemaster");

        jLabel6.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        jLabel6.setText("Secret Key");
        jLabel6.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);

        m_SecretKey.setText("abc123");

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(4, 4, 4)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(m_AMQPUrl, javax.swing.GroupLayout.PREFERRED_SIZE, 445, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel5)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(m_RoutingKey))
                    .addComponent(m_TransferUri, javax.swing.GroupLayout.DEFAULT_SIZE, 693, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(m_SecretKey, javax.swing.GroupLayout.PREFERRED_SIZE, 141, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(m_ConnectBtn))
                    .addComponent(m_SigningAlgorithm, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(m_AMQPUrl, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3)
                    .addComponent(m_ConnectBtn)
                    .addComponent(jLabel5)
                    .addComponent(m_RoutingKey, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel6)
                    .addComponent(m_SecretKey, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(m_TransferUri, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel4)
                    .addComponent(jLabel7)
                    .addComponent(m_SigningAlgorithm, javax.swing.GroupLayout.DEFAULT_SIZE, 32, Short.MAX_VALUE))
                .addContainerGap())
        );

        jSplitPane1.setDividerLocation(300);
        jSplitPane1.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        jSplitPane1.setResizeWeight(1.0);

        jPanel4.setPreferredSize(new java.awt.Dimension(1108, 300));

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addComponent(m_AgentPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(m_StatusPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 762, Short.MAX_VALUE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(m_StatusPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 299, Short.MAX_VALUE)
            .addComponent(m_AgentPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
        );

        jSplitPane1.setLeftComponent(jPanel4);
        jSplitPane1.setRightComponent(m_LogPanel);

        jPanel2.setPreferredSize(new java.awt.Dimension(400, 24));
        jPanel2.setLayout(new javax.swing.BoxLayout(jPanel2, javax.swing.BoxLayout.LINE_AXIS));

        m_ConnectProgressBar.setMaximumSize(new java.awt.Dimension(32767, 20));
        m_ConnectProgressBar.setMinimumSize(new java.awt.Dimension(128, 20));
        m_ConnectProgressBar.setPreferredSize(new java.awt.Dimension(128, 20));
        m_ConnectProgressBar.setString("");
        m_ConnectProgressBar.setStringPainted(true);
        jPanel2.add(m_ConnectProgressBar);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 1104, Short.MAX_VALUE)
                    .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 434, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        jMenu1.setMnemonic('F');
        jMenu1.setText("File");
        jMenu1.setActionCommand("");

        jMenuItem2.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        jMenuItem2.setMnemonic('C');
        jMenuItem2.setText("Clear Log");
        jMenuItem2.setToolTipText("");
        jMenuItem2.setActionCommand("file->clear_log");
        jMenuItem2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                onActionPerformed(evt);
            }
        });
        jMenu1.add(jMenuItem2);
        jMenu1.add(jSeparator2);

        jMenuItem3.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        jMenuItem3.setMnemonic('x');
        jMenuItem3.setText("Exit");
        jMenuItem3.setActionCommand("file->exit");
        jMenuItem3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                onActionPerformed(evt);
            }
        });
        jMenu1.add(jMenuItem3);

        jMenuBar1.add(jMenu1);

        jMenu2.setText("View");

        m_ShowMessageWindow.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        m_ShowMessageWindow.setText("Show Message Window");
        m_ShowMessageWindow.setActionCommand("view->show_message_window");
        m_ShowMessageWindow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                onActionPerformed(evt);
            }
        });
        jMenu2.add(m_ShowMessageWindow);

        jMenuBar1.add(jMenu2);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void onActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_onActionPerformed
		m_Listener.actionPerformed(evt);
    }//GEN-LAST:event_onActionPerformed

	private class _WindowAdapter extends WindowAdapter {

		@Override
		public void windowClosing(WindowEvent e) {
			AgentGUI.this.dispose();
		}
	}
//
//	/**
//	 * @param args the command line arguments
//	 */
//	public static void main(String args[]) {
//		SwingUtilities.invokeLater(() -> {
//			new AgentGUI().setVisible(true);
//		});
//	}

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenu jMenu2;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JTextField m_AMQPUrl;
    private au.edu.uq.rcc.nimrodg.debug.agent.AgentControlPanel m_AgentPanel;
    private javax.swing.JButton m_ConnectBtn;
    private javax.swing.JProgressBar m_ConnectProgressBar;
    private au.edu.uq.rcc.nimrodg.debug.agent.LogPanel m_LogPanel;
    private javax.swing.JTextField m_RoutingKey;
    private javax.swing.JTextField m_SecretKey;
    private javax.swing.JCheckBoxMenuItem m_ShowMessageWindow;
    private javax.swing.JComboBox<String> m_SigningAlgorithm;
    private au.edu.uq.rcc.nimrodg.debug.agent.AgentStatusPanel m_StatusPanel;
    private javax.swing.JTextField m_TransferUri;
    // End of variables declaration//GEN-END:variables
}
