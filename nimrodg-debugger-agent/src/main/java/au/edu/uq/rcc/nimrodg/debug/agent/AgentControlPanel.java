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

import au.edu.uq.rcc.nimrodg.agent.Agent;
import java.util.ArrayList;

public class AgentControlPanel extends javax.swing.JPanel {

	public interface Listener {

		void onSubmit(String job);

		void onPing();

		void onCancel();

		void onTerminate();

		void onReset();
	}

	private final ArrayList<Listener> m_Listeners;

	public AgentControlPanel() {
		m_Listeners = new ArrayList<>();
		initComponents();
		m_Reset.setEnabled(true);
	}

	public void addListener(Listener l) {
		m_Listeners.add(l);
	}

	public void removeListener(Listener l) {
		m_Listeners.remove(l);
	}

	public void setState(Agent.State state) {
		if(state == Agent.State.SHUTDOWN) {
			m_WaitingForAgent.setSelected(true);
			m_SendingInit.setSelected(true);
			m_Shutdown.setSelected(true);
			m_SubmitBtn.setEnabled(false);
			m_PingBtn.setEnabled(false);
			m_CancelBtn.setEnabled(false);
			m_TerminateBtn.setEnabled(false);
		} else if(state == Agent.State.WAITING_FOR_HELLO) {
			m_WaitingForAgent.setSelected(true);
			m_SendingInit.setSelected(false);
			m_Shutdown.setSelected(false);
			m_SubmitBtn.setEnabled(false);
			m_PingBtn.setEnabled(false);
			m_CancelBtn.setEnabled(false);
			m_TerminateBtn.setEnabled(false);
		} else if(state == Agent.State.READY) {
			m_SendingInit.setSelected(true);
			m_WaitingForAgent.setSelected(true);
			m_Shutdown.setSelected(false);
			m_SubmitBtn.setEnabled(true);
			m_PingBtn.setEnabled(true);
			m_CancelBtn.setEnabled(false);
			m_TerminateBtn.setEnabled(true);
		} else if(state == Agent.State.BUSY) {
			m_SendingInit.setSelected(true);
			m_WaitingForAgent.setSelected(true);
			m_Shutdown.setSelected(false);
			m_SubmitBtn.setEnabled(false);
			m_PingBtn.setEnabled(true);
			m_CancelBtn.setEnabled(true);
			m_TerminateBtn.setEnabled(true);
		}
	}

	@SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        m_WaitingForAgent = new javax.swing.JCheckBox();
        m_SendingInit = new javax.swing.JCheckBox();
        m_SubmitBtn = new javax.swing.JButton();
        m_CancelBtn = new javax.swing.JButton();
        m_TerminateBtn = new javax.swing.JButton();
        m_Shutdown = new javax.swing.JCheckBox();
        m_Reset = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        m_JobText = new javax.swing.JTextArea();
        m_PingBtn = new javax.swing.JButton();

        m_WaitingForAgent.setText("Waiting for agent connection...");
        m_WaitingForAgent.setEnabled(false);

        m_SendingInit.setText("Sending initialisation message...");
        m_SendingInit.setEnabled(false);

        m_SubmitBtn.setText("Submit");
        m_SubmitBtn.setEnabled(false);
        m_SubmitBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                m_SubmitBtnActionPerformed(evt);
            }
        });

        m_CancelBtn.setText("Cancel");
        m_CancelBtn.setEnabled(false);
        m_CancelBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                m_CancelBtnActionPerformed(evt);
            }
        });

        m_TerminateBtn.setText("Terminate");
        m_TerminateBtn.setEnabled(false);
        m_TerminateBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                m_TerminateBtnActionPerformed(evt);
            }
        });

        m_Shutdown.setText("Agent shutdown...");
        m_Shutdown.setEnabled(false);

        m_Reset.setText("Reset");
        m_Reset.setEnabled(false);
        m_Reset.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                m_ResetActionPerformed(evt);
            }
        });

        m_JobText.setColumns(20);
        m_JobText.setFont(new java.awt.Font("Courier New", 0, 10)); // NOI18N
        m_JobText.setRows(5);
        m_JobText.setText("parameter x text \"1\"\n\ntask main\n    redirect stdout to out\n    redirect stderr to out\n    exec uname -a\n    exec pwd\n    copy node:out root:out.txt\n    copy root:out.txt node:out2.txt\n    copy node:out2.txt node:out3.txt\nendtask"); // NOI18N
        jScrollPane2.setViewportView(m_JobText);

        m_PingBtn.setText("Ping");
        m_PingBtn.setEnabled(false);
        m_PingBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                m_PingBtnActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane2)
                    .addComponent(m_WaitingForAgent, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(m_SendingInit, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(m_Shutdown, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(m_Reset, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(m_SubmitBtn)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(m_PingBtn)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(m_CancelBtn)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(m_TerminateBtn))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(m_WaitingForAgent)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(m_SendingInit)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 209, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(m_SubmitBtn)
                    .addComponent(m_CancelBtn)
                    .addComponent(m_TerminateBtn)
                    .addComponent(m_PingBtn))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(m_Shutdown)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(m_Reset)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void m_SubmitBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_m_SubmitBtnActionPerformed
		m_Listeners.forEach((Listener l) -> {
			l.onSubmit(m_JobText.getText());
		});
    }//GEN-LAST:event_m_SubmitBtnActionPerformed

    private void m_CancelBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_m_CancelBtnActionPerformed
		m_Listeners.forEach(Listener::onCancel);
    }//GEN-LAST:event_m_CancelBtnActionPerformed

    private void m_TerminateBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_m_TerminateBtnActionPerformed
		m_Listeners.forEach(Listener::onTerminate);
    }//GEN-LAST:event_m_TerminateBtnActionPerformed

    private void m_ResetActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_m_ResetActionPerformed
		m_Listeners.forEach(Listener::onReset);
    }//GEN-LAST:event_m_ResetActionPerformed

    private void m_PingBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_m_PingBtnActionPerformed
        m_Listeners.forEach(Listener::onPing);
    }//GEN-LAST:event_m_PingBtnActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JButton m_CancelBtn;
    private javax.swing.JTextArea m_JobText;
    private javax.swing.JButton m_PingBtn;
    private javax.swing.JButton m_Reset;
    private javax.swing.JCheckBox m_SendingInit;
    private javax.swing.JCheckBox m_Shutdown;
    private javax.swing.JButton m_SubmitBtn;
    private javax.swing.JButton m_TerminateBtn;
    private javax.swing.JCheckBox m_WaitingForAgent;
    // End of variables declaration//GEN-END:variables
}
