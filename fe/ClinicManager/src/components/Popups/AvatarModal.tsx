import { useEffect, useState } from "react";
import Modal from "../Modal";
import { AVAILABLE_AVATARS } from "../../constants/avatars";
import UserService from "../../services/UserService";

export interface AvatarModalProps {
  currentAvatar?: string | null;
  onSelect: (avatar: string) => void;
  onClose: () => void;
  className?: string;
}

export function AvatarModal({
  currentAvatar = null,
  onSelect,
  onClose,
  className = "",
}: AvatarModalProps) {
  const [takenAvatars, setTakenAvatars] = useState<string[] | null>(null);

  useEffect(() => {
    UserService.getTakenAvatars()
      .then((data) => setTakenAvatars(data ?? []))
      .catch(() => setTakenAvatars([]));
  }, []);

  const avatarKeys = Object.keys(AVAILABLE_AVATARS).filter(
    (key) => key === currentAvatar || !takenAvatars?.includes(key),
  );

  return (
    <Modal
      title="Wybierz Avatar"
      onClose={onClose}
      size="md"
      className={className}
    >
      {takenAvatars === null ? (
        <span className="dl-loading">Ładowanie...</span>
      ) : (
        <div className="pf-avatar-grid">
          {avatarKeys.map((key) => (
            <button
              key={key}
              type="button"
              className={`pf-avatar-option flex align-items-center justify-center pointer ${
                currentAvatar === key ? "selected" : ""
              }`}
              onClick={() => onSelect(key)}
            >
              <img src={AVAILABLE_AVATARS[key]} alt={key} />
            </button>
          ))}
        </div>
      )}
    </Modal>
  );
}

export default AvatarModal;
