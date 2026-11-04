import React from "react";

export interface ActionButtonProps {
  src?: string;
  alt?: string;
  iconTitle?: string;
  text?: string;
  onClick: (e: React.MouseEvent) => void;
  disableImg?: boolean;
  disableText?: boolean;
  disableBorder?: boolean;
  disabled?: boolean;
  className?: string;
  default?: boolean;
  style?: React.CSSProperties;
  children?: React.ReactNode;
}

export function ActionButton(props: ActionButtonProps) {
  return (
    <div className={`product-action-button-container ${props.className? props.className : ""} flex align-items-center height-auto transparent`}
    title ={props.iconTitle}>
      <button className={`product-action-button ${props.className? props.className : ""} ${props.disabled ? "not-allowed" : props.default ? "default" : ""} ${props.disableText ? "no-text" : ""} transparent flex width-fit-content align-items-center justify-center g-05 pointer`}
        onClick={props.onClick}
        aria-label={props.iconTitle}
        disabled={props.disabled || false}
        style={props.disableBorder ? { border: "none", ...props.style } : props.style}
      >
        { !props.disableImg && props.src && <img
          src={props.src}
          alt={props.alt}
          title ={props.iconTitle}
          className={`product-action-button-icon ${props.text?.toLowerCase()} ${props.className? props.className : ""} ${props.disabled ? "not-allowed" : ""}`}
        ></img> }
        {props.children}
        {!(props.disableText || false) && props.text && (
          <span className={`product-action-button-text text-control nowrap ${props.className? props.className : ""}`}>{props.text}</span>
        )}
      </button>
    </div>
  );
};

export default ActionButton;
